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

/* Alex Miller, Dec 5, 2014 */

public class Cycle extends ASeq implements IReduce {

private final ISeq all;      // never null
private final ISeq current;  // never null
private volatile ISeq _next;  // cached
private final Cycle _head;

private Cycle(ISeq all, ISeq current, Cycle head){
    this.all = all;
    this.current = current;
    this._head = head;
}

private Cycle(IPersistentMap meta, ISeq all, ISeq current, Cycle head){
    super(meta);
    this.all = all;
    this.current = current;
    this._head = head;
}

public static ISeq create(ISeq vals){
    if(vals == null)
        return PersistentList.EMPTY;
    return new Cycle(vals, vals, null);
}

private Cycle head() {
    if (_head != null)
        return _head;
    if (current == all)
        return this;
    return null;
}

public Object first(){
    return current.first();
}

public ISeq next(){
    if(_next == null) {
        ISeq next = current.next();
        Cycle head = head();
        if (next != null)
            _next = new Cycle(all, next, head);
        else
            _next = head == null ? new Cycle(all, all, null) : head;
    }
    return _next;
}

public Cycle withMeta(IPersistentMap meta){
    return new Cycle(meta, all, current, null);
}

public Object reduce(IFn f){
    Object ret = current.first();
    ISeq s = current;
    while(true) {
        s = s.next();
        if(s == null)
            s = all;
        ret = f.invoke(ret, s.first());
        if(RT.isReduced(ret))
            return ((IDeref)ret).deref();
    }
}

public Object reduce(IFn f, Object start){
    Object ret = start;
    ISeq s = current;
    while(true){
        ret = f.invoke(ret, s.first());
        if(RT.isReduced(ret))
            return ((IDeref)ret).deref();
        s = s.next();
        if(s == null)
            s = all;
    }
}
}
