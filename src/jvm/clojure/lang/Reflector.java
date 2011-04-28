/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

/* rich Apr 19, 2006 */

package clojure.lang;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class Reflector{

private static Class getClass(Object o){
    return o == null ? null : o.getClass();
}

private static String getName(Class c){
    return c == null ? "null" : c.getName();
}

private static String toString(Class[] cs){
    StringBuffer sb = new StringBuffer(getName(cs[0]));
    for (int i = 1; i < cs.length; i++)
        {
        sb.append(",");
        sb.append(getName(cs[i]));
        }
    return sb.toString();
}

private static Class[] argTypes(Object[] args){
    Class[] cs = new Class[args.length];
    for (int i = 0; i < args.length; i++)
        cs[i] = getClass(args[i]);
    return cs;
}

/*
 * Functions to allow common code to work with both Method and Constructor instances
 */

private static Class[] getParameterTypes(Object member){
    if (member instanceof Method)
        return ((Method)member).getParameterTypes();
    else
        return ((Constructor)member).getParameterTypes();
}

private static Class getReturnType(Object member){
    if (member instanceof Method)
        return ((Method)member).getReturnType();
    else
        return ((Constructor)member).getDeclaringClass();
}

/* Enums to mitigate bugs from multiple boolean flags
 */

private enum Invoking{
    T(true), F(false);
    public final boolean b;
    private Invoking(boolean b){
        this.b = b;
    }
}
private enum Statics{
    T(true), F(false);
    public final boolean b;
    private Statics(boolean b){
        this.b = b;
    }
}

public static Object invokeInstanceMethod(Object target, String methodName, Object[] args) {
    return invokeMatchingMethod(target.getClass(), methodName, target, args, Statics.F);
}

private static Throwable getCauseOrElse(Exception e) {
	if (e.getCause() != null)
		return e.getCause();
	return e;
}

private static RuntimeException throwCauseOrElseException(Exception e) {
	if (e.getCause() != null)
		throw Util.sneakyThrow(e.getCause());
	throw Util.sneakyThrow(e);
}

public static Object invokeMethod(Object target, Method method, Object[] args){
    try
        {
        return prepRet(method.getReturnType(), method.invoke(target, boxArgs(method.getParameterTypes(), args)));
        }
    catch(InvocationTargetException e)
        {
        throw Util.sneakyThrow(getCauseOrElse(e));
        }
    catch(Exception e)
        {
        throw Util.sneakyThrow(e);
        }
}

private static Object invokeMatchingMethod(Class c, String methodName, Object target, Object[] args, Statics statics){
    Class[] argTypes = argTypes(args);
    Method method = getMatchingMethod(c, methodName, argTypes, statics, Invoking.T);
	return invokeMethod(target, method, args);
}

private static Method ensureMethodOfPublicBase(Class<?> c, Method m){
    if (Modifier.isPublic(m.getDeclaringClass().getModifiers()))
        return m;
    else
        {
        for(Class<?> iface : c.getInterfaces())
            {
            for(Method im : iface.getMethods())
                {
                if(im.getName().equals(m.getName())
                   && Arrays.equals(m.getParameterTypes(), im.getParameterTypes()))
                    {
                    return im;
                    }
                }
            }
        Class<?> sc = c.getSuperclass();
        if(sc == null)
            return null;
        for(Method scm : sc.getMethods())
            {
            if(scm.getName().equals(m.getName())
               && Arrays.equals(m.getParameterTypes(), scm.getParameterTypes())
               && Modifier.isPublic(scm.getDeclaringClass().getModifiers()))
                {
                return scm;
                }
            }
        Method n = ensureMethodOfPublicBase(sc, m); // look in parent
        if (n == null)
            throw new IllegalArgumentException("Can't call public method of non-public class: " +
                    m.toString());
        else
            return n;
        }
}

public static boolean isMatch(Method lhs, Method rhs) {
	if(!lhs.getName().equals(rhs.getName())
			|| !Modifier.isPublic(lhs.getDeclaringClass().getModifiers()))
		{
		return false;
		}

		Class[] types1 = lhs.getParameterTypes();
		Class[] types2 = rhs.getParameterTypes();
		if(types1.length != types2.length)
			return false;

		boolean match = true;
		for (int i=0; i<types1.length; ++i)
			{
			if(!types1[i].isAssignableFrom(types2[i]))
				{
				match = false;
				break;
				}
			}
		return match;
}

public static Object newInstance(Constructor ctor, Object[] args){
    try
        {
        return ctor.newInstance(boxArgs(ctor.getParameterTypes(), args));
        }
    catch(InvocationTargetException e)
        {
          throw Util.sneakyThrow(getCauseOrElse(e));
        }
    catch(Exception e)
        {
        throw Util.sneakyThrow(e);
        }
}

public static Object invokeConstructor(Class c, Object[] args) {
    Class[] argTypes = argTypes(args);
    Constructor ctor = getMatchingConstructor(c, argTypes, Invoking.T);
    return newInstance(ctor, args);
}

public static Object invokeStaticMethod(String className, String methodName, Object[] args) {
	Class c = RT.classForName(className);
	return invokeStaticMethod(c, methodName, args);
}

public static Object invokeStaticMethod(Class c, String methodName, Object[] args) {
	if(methodName.equals("new"))
		return invokeConstructor(c, args);
	return invokeMatchingMethod(c, methodName, c, args, Statics.T);
}

private static Object getFieldValue(Class c, String fieldName, Object target, Statics statics){
    Field f = getField(c, fieldName, statics, Invoking.T);
    try
        {
        return prepRet(f.getType(), f.get(target));
        }
    catch(Exception e)
        {
        throw Util.sneakyThrow(e);
        }
}

private static Object setFieldValue(Class c, String fieldName, Object target, Object val, Statics statics){
    Field f = getField(c, fieldName, statics, Invoking.T);
    try
        {
        f.set(target, boxArg(f.getType(), val));
        return val;
        }
    catch(Exception e)
        {
        throw Util.sneakyThrow(e);
        }
}

public static Object getStaticField(String className, String fieldName) {
	Class c = RT.classForName(className);
	return getStaticField(c, fieldName);
}

public static Object getStaticField(Class c, String fieldName) {
    return getFieldValue(c, fieldName, c, Statics.T);
}

public static Object setStaticField(String className, String fieldName, Object val) {
	Class c = RT.classForName(className);
	return setStaticField(c, fieldName, val);
}

public static Object setStaticField(Class c, String fieldName, Object val) {
	return setFieldValue(c, fieldName, c, val, Statics.T);
}

public static Object getInstanceField(Object target, String fieldName) {
    return getFieldValue(target.getClass(), fieldName, target, Statics.F);
}

public static Object setInstanceField(Object target, String fieldName, Object val) {
    return setFieldValue(target.getClass(), fieldName, target, val, Statics.F);
}

private static final Class[] EMPTY_TYPES = new Class[0];

// not used as of Clojure 1.6, but left for runtime compatibility with
// compiled bytecode from older versions
public static Object invokeNoArgInstanceMember(Object target, String name) {
	return invokeNoArgInstanceMember(target, name, false);
}

public static Object invokeNoArgInstanceMember(Object target, String name, boolean requireField) {
	Class c = target.getClass();

	if(requireField) {
		Field f = getField(c, name, false);
		if(f != null)
			return getInstanceField(target, name);
		else
			throw new IllegalArgumentException("No matching field found: " + name
					+ " for " + target.getClass());
	} else {
		Method m = getMatchingInstanceMethod(target.getClass(), name, EMPTY_TYPES);
		if(m != null)
			return invokeMethod(target, m, EMPTY_TYPES);
		else
			return getInstanceField(target, name);
	}
}

private static Field getField(Class c, String fieldName, Statics statics, Invoking invoking){
    for(Field f : c.getFields())
        {
        if(fieldName.equals(f.getName())
           && Modifier.isStatic(f.getModifiers()) == statics.b)
            return f;
        }

    if (invoking.b)
        throw new IllegalArgumentException("No "+fieldName+" field found in "+c.getName());

    return null;
}

static public Field getField(Class c, String name, boolean getStatics){
	return getField(c, name, getStatics ? Statics.T : Statics.F, Invoking.F);
}

private static boolean subsumes(Class[] c1, Class[] c2){
	//presumes matching lengths
	Boolean better = false;
	for(int i = 0; i < c1.length; i++)
		{
		if(c1[i] != c2[i])// || c2[i].isPrimitive() && c1[i] == Object.class))
			{
			if(!c1[i].isPrimitive() && c2[i].isPrimitive()
			   //|| Number.class.isAssignableFrom(c1[i]) && c2[i].isPrimitive()
			   ||
			   c2[i].isAssignableFrom(c1[i]))
				better = true;
			else
				return false;
			}
		}
	return better;
}

private static <T extends Object> int getMatchingParams(T member, ArrayList<Class[]> paramlists, Class[] argTypes,
                             List<Class> rets)
		{
	//presumes matching lengths
	int matchIdx = -1;
	boolean tied = false;
    boolean foundExact = false;
	for(int i = 0; i < paramlists.size(); i++)
		{
		boolean match = true;
		int exact = 0;
		for(int p = 0; match && p < argTypes.length; ++p)
			{
			Class aclass = argTypes[p];
			Class pclass = paramlists.get(i)[p];
			if(aclass == pclass)
				exact++;
			else
				match = paramArgTypeMatch(pclass, aclass);
			}
		if(exact == argTypes.length)
            {
            if(!foundExact || matchIdx == -1 || rets.get(matchIdx).isAssignableFrom(rets.get(i)))
                matchIdx = i;
            tied = false;
            foundExact = true;
            }
		else if(match && !foundExact)
			{
			if(matchIdx == -1)
				matchIdx = i;
			else
				{
				if(subsumes(paramlists.get(i), paramlists.get(matchIdx)))
					{
					matchIdx = i;
					tied = false;
					}
				else if(Arrays.equals(paramlists.get(matchIdx), paramlists.get(i)))
					{
					if(rets.get(matchIdx).isAssignableFrom(rets.get(i)))
						matchIdx = i;
					}
				else if(!(subsumes(paramlists.get(matchIdx), paramlists.get(i))))
						tied = true;
				}
			}
		}
	if(tied)
	    {
        if (member instanceof Method)
            {
            Method m = ((Method)member);
            Class<?> c = m.getDeclaringClass();
            throw new IllegalArgumentException("Found multiple "+m.getName()+" methods in "+c.getName()+" for argtypes: "+toString(argTypes));
            }
        else
            {
            Constructor<?> ctor = ((Constructor<?>)member);
            Class<?> c = ctor.getDeclaringClass();
            throw new IllegalArgumentException("Found multiple constructors in "+c.getName()+" for argtypes: "+toString(argTypes));
            }
	    }

	return matchIdx;
}

private static <T extends Object> T getMatchingMember(List<T> members, Class<?>[] argTypes, Invoking invoking){
    ArrayList<T> matches = new ArrayList();
    ArrayList<Class[]> params = new ArrayList();
    ArrayList<Class> rets = new ArrayList();
    for(T member : members)
        {
        if(getParameterTypes(member).length == argTypes.length)
            {
            matches.add(member);
            params.add(getParameterTypes(member));
            rets.add(getReturnType(member));
            }
        }

    int matchidx = -1;
    if (matches.size() == 1)
        {
        matchidx = 0;
        }
    if(matches.size() > 1)
        {
        matchidx = getMatchingParams(matches.get(0), params, argTypes, rets);
        }

    T match = matchidx >= 0 ? matches.get(matchidx) : null;

    return match;
}

private static Constructor getMatchingConstructor(Class c, Class[] argTypes, Invoking invoking){
    List<Constructor> ctors = Arrays.asList(c.getConstructors());
    Constructor ctor = getMatchingMember(ctors, argTypes, invoking);

    if (ctor == null && invoking.b)
        throw new IllegalArgumentException("No matching constructor found in "+c.getName() + " for argtypes: " + toString(argTypes));
    return ctor;
}

public static Constructor getMatchingConstructor(Class c, Class[] argTypes){
    return getMatchingConstructor(c, argTypes, Invoking.F);
}

private static List<Method> getMethodsForName(Class c, String name, Statics statics){
	Method[] allmethods = c.getMethods();
	ArrayList methods = new ArrayList();
	ArrayList bridgeMethods = new ArrayList();
	for(int i = 0; i < allmethods.length; i++)
		{
		Method method = allmethods[i];
		if(name.equals(method.getName())
		   && Modifier.isStatic(method.getModifiers()) == statics.b)
			{
			try
				{
				if(method.isBridge()
				   && c.getMethod(method.getName(), method.getParameterTypes())
						.equals(method))
					bridgeMethods.add(method);
				else
					methods.add(method);
				}
			catch(NoSuchMethodException e)
				{
				}
			}
		}

	if(methods.isEmpty())
		methods.addAll(bridgeMethods);
	
	if(!statics.b && c.isInterface())
		{
		allmethods = Object.class.getMethods();
		for(int i = 0; i < allmethods.length; i++)
			{
			if(name.equals(allmethods[i].getName())
			   && Modifier.isStatic(allmethods[i].getModifiers()) == statics.b)
				{
				methods.add(allmethods[i]);
				}
			}
		}
	return methods;
}

private static Method getMatchingMethod(Class c, String methodName, Class[] argTypes, Statics statics, Invoking invoking){
    List<Method> methods = getMethodsForName(c, methodName, statics);
    Method m = getMatchingMember(methods, argTypes, invoking);

    if(m != null)
        m = ensureMethodOfPublicBase(c, m);
    if (m == null && invoking.b)
        throw new IllegalArgumentException("No matching "+methodName+" method found in "+c.getName() + " for argtypes: " + toString(argTypes));
    return m;
}

public static Method getMatchingInstanceMethod(Class c, String methodName, Class[] argTypes){
    return getMatchingMethod(c, methodName, argTypes, Statics.F, Invoking.F);
}

public static Method getMatchingStaticMethod(Class c, String methodName, Class[] argTypes){
    return getMatchingMethod(c, methodName, argTypes, Statics.T, Invoking.F);
}

private static Object boxArg(Class paramType, Object arg){
	if(!paramType.isPrimitive())
		return paramType.cast(arg);
	else if(paramType == boolean.class)
		return Boolean.class.cast(arg);
	else if(paramType == char.class)
		return Character.class.cast(arg);
	else if(arg instanceof Number)
		{
		Number n = (Number) arg;
		if(paramType == int.class)
			return n.intValue();
		else if(paramType == float.class)
			return n.floatValue();
		else if(paramType == double.class)
			return n.doubleValue();
		else if(paramType == long.class)
			return n.longValue();
		else if(paramType == short.class)
			return n.shortValue();
		else if(paramType == byte.class)
			return n.byteValue();
		}
	throw new IllegalArgumentException("Unexpected param type, expected: " + paramType +
	                                   ", given: " + arg.getClass().getName());
}

private static Object[] boxArgs(Class[] params, Object[] args){
	if(params.length == 0)
		return null;
	Object[] ret = new Object[params.length];
	for(int i = 0; i < params.length; i++)
		{
		Object arg = args[i];
		Class paramType = params[i];
		ret[i] = boxArg(paramType, arg);
		}
	return ret;
}

private static boolean paramArgTypeMatch(Class paramType, Class argType){
	if(argType == null)
		return !paramType.isPrimitive();
	if(paramType == argType || paramType.isAssignableFrom(argType))
		return true;
	if(paramType == int.class)
		return argType == Integer.class
		       || argType == long.class
				|| argType == Long.class
				|| argType == short.class
				|| argType == byte.class;// || argType == FixNum.class;
	else if(paramType == float.class)
		return argType == Float.class
				|| argType == double.class;
	else if(paramType == double.class)
		return argType == Double.class
				|| argType == float.class;// || argType == DoubleNum.class;
	else if(paramType == long.class)
		return argType == Long.class
				|| argType == int.class
				|| argType == short.class
				|| argType == byte.class;// || argType == BigNum.class;
	else if(paramType == char.class)
		return argType == Character.class;
	else if(paramType == short.class)
		return argType == Short.class;
	else if(paramType == byte.class)
		return argType == Byte.class;
	else if(paramType == boolean.class)
		return argType == Boolean.class;
	return false;
}

public static Object prepRet(Class c, Object x){
	if (!(c.isPrimitive() || c == Boolean.class))
		return x;
	if(x instanceof Boolean)
		return ((Boolean) x)?Boolean.TRUE:Boolean.FALSE;
//	else if(x instanceof Integer)
//		{
//		return ((Integer)x).longValue();
//		}
//	else if(x instanceof Float)
//			return Double.valueOf(((Float) x).doubleValue());
	return x;
}
}
