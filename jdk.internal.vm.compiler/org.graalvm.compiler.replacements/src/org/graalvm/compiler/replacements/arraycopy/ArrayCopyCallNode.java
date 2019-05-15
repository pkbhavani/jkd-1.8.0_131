/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
//JaCoCo Exclude


package org.graalvm.compiler.replacements.arraycopy;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.GetObjectAddressNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.memory.AbstractMemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.MemoryNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.word.WordTypes;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;

@NodeInfo(allowedUsageTypes = {Memory}, cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public final class ArrayCopyCallNode extends AbstractMemoryCheckpoint implements Lowerable, MemoryCheckpoint.Single, MemoryAccess, Canonicalizable {

    public static final NodeClass<ArrayCopyCallNode> TYPE = NodeClass.create(ArrayCopyCallNode.class);
    @Input protected ValueNode src;
    @Input protected ValueNode srcPos;
    @Input protected ValueNode dest;
    @Input protected ValueNode destPos;
    @Input protected ValueNode length;

    @OptionalInput(Memory) MemoryNode lastLocationAccess;

    private final JavaKind elementKind;
    private final LocationIdentity locationIdentity;
    private final ArrayCopyForeignCalls foreignCalls;
    private final JavaKind wordJavaKind;
    private final int heapWordSize;

    /**
     * Aligned means that the offset of the copy is heap word aligned.
     */
    private boolean aligned;
    private boolean disjoint;
    private boolean uninitialized;

    public ArrayCopyCallNode(@InjectedNodeParameter ArrayCopyForeignCalls foreignCalls, @InjectedNodeParameter WordTypes wordTypes,
                    ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos,
                    ValueNode length, JavaKind elementKind, boolean aligned, boolean disjoint, boolean uninitialized, int heapWordSize) {
        this(foreignCalls, wordTypes, src, srcPos, dest, destPos, length, elementKind, null, aligned, disjoint, uninitialized, heapWordSize);
    }

    protected ArrayCopyCallNode(@InjectedNodeParameter ArrayCopyForeignCalls foreignCalls, @InjectedNodeParameter WordTypes wordTypes,
                    ValueNode src, ValueNode srcPos, ValueNode dest,
                    ValueNode destPos, ValueNode length, JavaKind elementKind,
                    LocationIdentity locationIdentity, boolean aligned, boolean disjoint, boolean uninitialized, int heapWordSize) {
        super(TYPE, StampFactory.forVoid());
        assert elementKind != null;
        this.src = src;
        this.srcPos = srcPos;
        this.dest = dest;
        this.destPos = destPos;
        this.length = length;
        this.elementKind = elementKind;
        this.locationIdentity = (locationIdentity != null ? locationIdentity : NamedLocationIdentity.getArrayLocation(elementKind));
        this.aligned = aligned;
        this.disjoint = disjoint;
        this.uninitialized = uninitialized;
        this.foreignCalls = foreignCalls;
        this.wordJavaKind = wordTypes.getWordKind();
        this.heapWordSize = heapWordSize;

    }

    public ValueNode getSource() {
        return src;
    }

    public ValueNode getSourcePosition() {
        return srcPos;
    }

    public ValueNode getDestination() {
        return dest;
    }

    public ValueNode getDestinationPosition() {
        return destPos;
    }

    public ValueNode getLength() {
        return length;
    }

    public JavaKind getElementKind() {
        return elementKind;
    }

    private ValueNode computeBase(LoweringTool tool, ValueNode base, ValueNode pos) {
        FixedWithNextNode basePtr = graph().add(new GetObjectAddressNode(base));
        graph().addBeforeFixed(this, basePtr);
        Stamp wordStamp = StampFactory.forKind(wordJavaKind);
        ValueNode wordPos = IntegerConvertNode.convert(pos, wordStamp, graph(), NodeView.DEFAULT);
        int shift = CodeUtil.log2(tool.getMetaAccess().getArrayIndexScale(elementKind));
        ValueNode scaledIndex = graph().unique(new LeftShiftNode(wordPos, ConstantNode.forInt(shift, graph())));
        ValueNode offset = graph().unique(new AddNode(scaledIndex, ConstantNode.forIntegerStamp(wordStamp, tool.getMetaAccess().getArrayBaseOffset(elementKind), graph())));
        return graph().unique(new OffsetAddressNode(basePtr, offset));
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage().areFrameStatesAtDeopts()) {
            updateAlignedDisjoint(tool.getMetaAccess());
            ForeignCallDescriptor desc = foreignCalls.lookupArraycopyDescriptor(elementKind, isAligned(), isDisjoint(), isUninitialized(),
                            locationIdentity.equals(LocationIdentity.any()));
            StructuredGraph graph = graph();
            ValueNode srcAddr = computeBase(tool, getSource(), getSourcePosition());
            ValueNode destAddr = computeBase(tool, getDestination(), getDestinationPosition());
            ValueNode len = getLength();
            if (len.stamp(NodeView.DEFAULT).getStackKind() != JavaKind.Long) {
                len = IntegerConvertNode.convert(len, StampFactory.forKind(JavaKind.Long), graph(), NodeView.DEFAULT);
            }
            ForeignCallNode call = graph.add(new ForeignCallNode(foreignCalls, desc, srcAddr, destAddr, len));
            call.setStateAfter(stateAfter());
            graph.replaceFixedWithFixed(this, call);
        }
    }

    @Override
    public MemoryNode getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryNode lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @NodeIntrinsic
    private static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind, @ConstantNodeParameter boolean aligned,
                    @ConstantNodeParameter boolean disjoint, @ConstantNodeParameter boolean uninitialized, @ConstantNodeParameter int heapWordSize);

    @NodeIntrinsic
    private static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind,
                    @ConstantNodeParameter LocationIdentity locationIdentity, @ConstantNodeParameter boolean aligned, @ConstantNodeParameter boolean disjoint,
                    @ConstantNodeParameter boolean uninitialized, @ConstantNodeParameter int heapWordSize);

    public static void arraycopyObjectKillsAny(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter int heapWordSize) {
        arraycopy(src, srcPos, dest, destPos, length, JavaKind.Object, LocationIdentity.any(), false, false, false, heapWordSize);
    }

    public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind, @ConstantNodeParameter int heapWordSize) {
        arraycopy(src, srcPos, dest, destPos, length, elementKind, false, false, false, heapWordSize);
    }

    public static void disjointArraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind, @ConstantNodeParameter int heapWordSize) {
        arraycopy(src, srcPos, dest, destPos, length, elementKind, false, true, false, heapWordSize);
    }

    public static void disjointUninitializedArraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind,
                    @ConstantNodeParameter int heapWordSize) {
        arraycopy(src, srcPos, dest, destPos, length, elementKind, false, true, true, heapWordSize);
    }

    public boolean isAligned() {
        return aligned;
    }

    public boolean isDisjoint() {
        return disjoint;
    }

    public boolean isUninitialized() {
        return uninitialized;
    }

    boolean isHeapWordAligned(MetaAccessProvider metaAccess, JavaConstant value, JavaKind kind) {
        return (metaAccess.getArrayBaseOffset(kind) + (long) value.asInt() * metaAccess.getArrayIndexScale(kind)) % heapWordSize == 0;
    }

    public void updateAlignedDisjoint(MetaAccessProvider metaAccess) {
        JavaKind componentKind = elementKind;
        if (srcPos == destPos) {
            // Can treat as disjoint
            disjoint = true;
        }
        PrimitiveConstant constantSrc = (PrimitiveConstant) srcPos.stamp(NodeView.DEFAULT).asConstant();
        PrimitiveConstant constantDst = (PrimitiveConstant) destPos.stamp(NodeView.DEFAULT).asConstant();
        if (constantSrc != null && constantDst != null) {
            if (!aligned) {
                aligned = isHeapWordAligned(metaAccess, constantSrc, componentKind) && isHeapWordAligned(metaAccess, constantDst, componentKind);
            }
            if (constantSrc.asInt() >= constantDst.asInt()) {
                // low to high copy so treat as disjoint
                disjoint = true;
            }
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (getLength().isConstant() && getLength().asConstant().isDefaultForKind()) {
            if (lastLocationAccess != null) {
                replaceAtUsages(InputType.Memory, lastLocationAccess.asNode());
            }
            return null;
        }
        return this;
    }
}
