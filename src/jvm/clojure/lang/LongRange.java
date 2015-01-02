/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package clojure.lang;

import java.io.Serializable;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Implements the special common case of a finite range based on long start, end, and step.
 */
public class LongRange extends ARange implements IChunkedSeq {

private static final int CHUNK_SIZE = 32;

// Invariants guarantee this is never an empty or infinite seq
//   assert(start != end && step != 0)
final long start;
final long end;
final long step;
final BoundsCheck boundsCheck;
volatile LongArrayChunk _chunk;    // created lazily, only when chunking

private static interface BoundsCheck extends Serializable {
    boolean exceededBounds(long val);
}

private static BoundsCheck positiveStep(final long end) {
    return new BoundsCheck() {
        public boolean exceededBounds(long val){
            return (val >= end);
        }
    };
}

private static BoundsCheck negativeStep(final long end) {
    return new BoundsCheck() {
        public boolean exceededBounds(long val){
            return (val <= end);
        }
    };
}

private LongRange(long start, long end, long step, BoundsCheck boundsCheck){
    this.start = start;
    this.end = end;
    this.step = step;
    this.boundsCheck = boundsCheck;
}

private LongRange(IPersistentMap meta, long start, long end, long step, BoundsCheck boundsCheck, LongArrayChunk chunk){
    super(meta);
    this.start = start;
    this.end = end;
    this.step = step;
    this.boundsCheck = boundsCheck;
    this._chunk = chunk;
}

public static ISeq create(long end) {
    if(end > 0)
        return new LongRange(0L, end, 1L, positiveStep(end));
    else
        return PersistentList.EMPTY;
}

public static ISeq create(long start, long end) {
    if(start >= end)
        return PersistentList.EMPTY;
    else
        return new LongRange(start, end, 1L, positiveStep(end));
}

private static Var REPEAT = RT.var("clojure.core", "repeat");

public static ISeq create(final long start, long end, long step) {
    if(step > 0) {
        if(end <= start) return PersistentList.EMPTY;
        return new LongRange(start, end, step, positiveStep(end));
    } else if(step < 0) {
        if(end >= start) return PersistentList.EMPTY;
        return new LongRange(start, end, step, negativeStep(end));
    } else {
        if(end == start) return PersistentList.EMPTY;
        //return Repeat.create(start);    // alternate impl when Repeat exists
        return (ISeq) REPEAT.invoke(start);
    }
}

public Obj withMeta(IPersistentMap meta){
    if(meta == _meta)
        return this;
    return new LongRange(meta, start, end, step, boundsCheck, _chunk);
}

public Object first() {
    return start;
}

public ISeq next() {
    long next = start + step;
    if(boundsCheck.exceededBounds(next))
        return null;
    else
        return new LongRange(next, end, step, boundsCheck);
}

public int count() {
    double c = (end - start) / step;
    int ic = (int) c;
    if(c < ic)
        return ic + 1;
    else
        return ic;
}

public Object reduce(IFn f) {
    Object acc = start;
    long i = start + step;
    while(! boundsCheck.exceededBounds(i)) {
        acc = f.invoke(acc, i);
        if (acc instanceof Reduced) return ((Reduced)acc).deref();
        i += step;
    }
    return acc;
}

public Object reduce(IFn f, Object val) {
    Object acc = val;
    long i = start;
    do {
        acc = f.invoke(acc, i);
        if (RT.isReduced(acc)) return ((Reduced)acc).deref();
        i += step;
    } while(! boundsCheck.exceededBounds(i));
    return acc;
}

public Iterator iterator() {
    return new LongRangeIterator();
}

class LongRangeIterator implements Iterator {
    private long next;

    public LongRangeIterator() {
        this.next = start;
    }

    public boolean hasNext() {
        return(! boundsCheck.exceededBounds(next));
    }

    public Object next() {
        if (hasNext()) {
            long ret = next;
            next = next + step;
            return ret;
        } else {
            throw new NoSuchElementException();
        }
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}

public ISeq seq() {
    return this;
}

//////////// Chunked Seq Stuff //////////

private LongArrayChunk ensureChunk() {
    if(_chunk == null)
        _chunk = LongArrayChunk.create(start, step, boundsCheck);
    return _chunk;
}

public IChunk chunkedFirst() {
    return ensureChunk();
}

public ISeq chunkedNext() {
    long nextStart = ensureChunk().last() + step;
    if(boundsCheck.exceededBounds(nextStart))
        return null;
    else
        return new LongRange(nextStart, end, step, boundsCheck);
}

public ISeq chunkedMore() {
    ISeq next = chunkedNext();
    if(next == null)
        return PersistentList.EMPTY;
    return next;
}

// same as ArrayChunk, but with long[]
private static class LongArrayChunk implements IChunk, Serializable {
    final long[] array;
    final int off;
    final int end;

    public LongArrayChunk(long[] array){
        this(array, 0, array.length);
    }

    public LongArrayChunk(long[] array, int off){
        this(array, off, array.length);
    }

    public LongArrayChunk(long[] array, int off, int end){
        this.array = array;
        this.off = off;
        this.end = end;
    }

    static LongArrayChunk create(long start, long step, BoundsCheck boundsCheck) {
        long[] arr = new long[CHUNK_SIZE];
        int i = 0;
        long val = start;
        do {
            arr[i] = val;
            i++;
            val += step;
        } while(! boundsCheck.exceededBounds(val) && i < CHUNK_SIZE);
        return new LongArrayChunk(arr, 0, i);
    }

    public long last() {
        return array[end - 1];
    }

    public Object nth(int i){
        return array[off + i];
    }

    public Object nth(int i, Object notFound){
        if(i >= 0 && i < count())
            return nth(i);
        return notFound;
    }

    public int count(){
        return end - off;
    }

    public IChunk dropFirst(){
        if(off==end)
            throw new IllegalStateException("dropFirst of empty chunk");
        return new LongArrayChunk(array, off + 1, end);
    }

    public Object reduce(IFn f, Object start) {
        Object ret = f.invoke(start, array[off]);
        if(RT.isReduced(ret))
            return ret;
        for(int x = off + 1; x < end; x++) {
            ret = f.invoke(ret, array[x]);
            if(RT.isReduced(ret))
                return ret;
        }
        return ret;
    }
}

}