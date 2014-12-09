/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

/* rich May 14, 2008 */

package clojure.lang;

public class LazilyPersistentVector{


static public IPersistentVector createOwning(Object... items){
	switch (items.length) {
		case 0: return PersistentUnrolledVector.EMPTY;
		case 1: return PersistentUnrolledVector.create(items[0]);
		case 2: return PersistentUnrolledVector.create(items[0], items[1]);
		case 3: return PersistentUnrolledVector.create(items[0], items[1], items[2]);
		case 4: return PersistentUnrolledVector.create(items[0], items[1], items[2], items[3]);
		case 5: return PersistentUnrolledVector.create(items[0], items[1], items[2], items[3], items[4]);
		case 6: return PersistentUnrolledVector.create(items[0], items[1], items[2], items[3], items[4], items[5]);
	}
	if(items.length <= 32)
		return new PersistentVector(items.length, 5, PersistentVector.EMPTY_NODE,items);
	return PersistentVector.create(items);
}

static public IPersistentVector create(Object obj){
   if(obj instanceof IReduceInit)
       return PersistentVector.create((IReduceInit) obj);
   else if(obj instanceof ISeq)
       return PersistentVector.create(RT.seq(obj));
   else if(obj instanceof Iterable)
       return PersistentVector.create((Iterable)obj);
   else
       return createOwning(RT.toArray(obj));
}

}
