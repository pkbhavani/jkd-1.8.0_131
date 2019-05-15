/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 */

// -- This file was mechanically generated: Do not edit! -- //

package java.nio;

import jdk.internal.misc.Unsafe;


class ByteBufferAsDoubleBufferRL                  // package-private
    extends ByteBufferAsDoubleBufferL
{







    ByteBufferAsDoubleBufferRL(ByteBuffer bb) {   // package-private












        super(bb);

    }

    ByteBufferAsDoubleBufferRL(ByteBuffer bb,
                                     int mark, int pos, int lim, int cap,
                                     long addr)
    {






        super(bb, mark, pos, lim, cap, addr);

    }

    @Override
    Object base() {
        return bb.hb;
    }

    public DoubleBuffer slice() {
        int pos = this.position();
        int lim = this.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        long addr = byteOffset(pos);
        return new ByteBufferAsDoubleBufferRL(bb, -1, 0, rem, rem, addr);
    }

    public DoubleBuffer duplicate() {
        return new ByteBufferAsDoubleBufferRL(bb,
                                                    this.markValue(),
                                                    this.position(),
                                                    this.limit(),
                                                    this.capacity(),
                                                    address);
    }

    public DoubleBuffer asReadOnlyBuffer() {








        return duplicate();

    }


































    public DoubleBuffer put(double x) {






        throw new ReadOnlyBufferException();

    }

    public DoubleBuffer put(int i, double x) {






        throw new ReadOnlyBufferException();

    }

    public DoubleBuffer compact() {

















        throw new ReadOnlyBufferException();

    }

    public boolean isDirect() {
        return bb.isDirect();
    }

    public boolean isReadOnly() {
        return true;
    }











































    public ByteOrder order() {




        return ByteOrder.LITTLE_ENDIAN;

    }






}
