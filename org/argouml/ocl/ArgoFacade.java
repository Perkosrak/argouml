// $Id$
// Copyright (c) 1996-2005 The Regents of the University of California. All
// Rights Reserved. Permission to use, copy, modify, and distribute this
// software and its documentation without fee, and without a written
// agreement is hereby granted, provided that the above copyright notice
// and this paragraph appear in all copies.  This software program and
// documentation are copyrighted by The Regents of the University of
// California. The software program and documentation are supplied "AS
// IS", without any accompanying services from The Regents. The Regents
// does not warrant that the operation of the program will be
// uninterrupted or error-free. The end-user understands that the program
// was developed for research purposes and is advised not to rely
// exclusively on the program for any reason.  IN NO EVENT SHALL THE
// UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
// SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
// ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
// THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
// SUCH DAMAGE. THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
// PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
// CALIFORNIA HAS NO OBLIGATIONS TO PROVIDE MAINTENANCE, SUPPORT,
// UPDATES, ENHANCEMENTS, OR MODIFICATIONS.

package org.argouml.ocl;

import java.util.Collection;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Model;
import org.argouml.model.ModelFacade;

import tudresden.ocl.check.OclTypeException;
import tudresden.ocl.check.types.Any;
import tudresden.ocl.check.types.Basic;
import tudresden.ocl.check.types.Type;
import tudresden.ocl.check.types.Type2;

/**
 * Provides a facade of the ArgoUml uml model for the OCL compiler.
 *
 * <p>Note: 
 * In this file we have two different Collections active:
 * java.util.Collection and tudresden.ocl.check.types.Collection. Be
 * sure to explicitly specify what you mean every time.
 * import java.util.Collection;
 * import tudresden.ocl.check.types.Collection;
 * We also have two different ModelFacade but just one of them is imported.
 */
public class ArgoFacade implements tudresden.ocl.check.types.ModelFacade {

    /**
     * The target that this instance is connected to.
     */
    private Object target;

    /**
     * Construtor.
     *
     * @param t The target to create this facade for.
     */
    public ArgoFacade(Object t) {
	if (ModelFacade.isAClassifier(t)) {
	    target = t;
	}
    }

    /**
     * @see tudresden.ocl.check.types.ModelFacade#getClassifier(java.lang.String)
     */
    public Any getClassifier(String name) {
	Project p = ProjectManager.getManager().getCurrentProject();

	if (target != null && ModelFacade.getName(target).equals(name)) {
	    return new ArgoAny(target);
	} else {
	    /*
	     * Changed 2001-10-18 STEFFEN ZSCHALER
	     *
	     * Was:
	     *
	     MClassifier classifier = p.findTypeInModel(name,
	     p.getCurrentNamespace());
	     *
	     */
	    Object classifier = p.findTypeInModel(name, p.getModel());

	    if (classifier == null) {
		/**
		 * Added search in defined types 2001-10-18 STEFFEN ZSCHALER.
		 */
		classifier = p.findType(name, false);

		if (classifier == null) {
		    throw new OclTypeException("cannot find classifier: "
					       + name);
		}
	    }

	    return new ArgoAny(classifier);
	}
    }
}

/**
 * A class that is the wrapper for any type.
 */
class ArgoAny implements Any, Type2 {
    /**
     * Logger for the ArgoAny class.
     */
    private static final Logger LOG = Logger.getLogger(ArgoAny.class);

    private Object classifier;

    /**
     * Constructor.
     *
     * @param cl The ArgoUML classifier.
     */
    ArgoAny(Object cl) {
	classifier = cl;
    }

    /**
     * @see tudresden.ocl.check.types.Type#navigateQualified(
     *         java.lang.String, tudresden.ocl.check.types.Type[])
     */
    public Type navigateQualified(String name, Type[] qualifiers)
	throws OclTypeException {

	if (classifier == null) {
	    throw new OclTypeException("attempting to access features of Void");
	}


	if (qualifiers != null) {
	    throw new OclTypeException("qualified associations "
				       + "not supported yet!");
	}

	Type type = Basic.navigateAnyQualified(name, this, qualifiers);
	if (type != null)  {
	    return type;
	}

	Object foundAssocType = null, foundAttribType = null; // MClassifiers
	boolean isSet = false, isSequence = false; // cannot be Bag

	// first search for appropriate attributes
	java.util.Collection attributes =
	    Model.getUmlHelper().getCore().getAttributesInh(classifier);
	Iterator iter = attributes.iterator();
	while (iter.hasNext() && foundAttribType == null) {
	    Object attr = iter.next();
	    if (ModelFacade.getName(attr).equals(name)) {
		foundAttribType = ModelFacade.getType(attr);
	    }
	}

	// look for associations
	java.util.Collection associationEnds =
	    Model.getUmlHelper().getCore().getAssociateEndsInh(classifier);
	Iterator asciter = associationEnds.iterator();
	while (asciter.hasNext() && foundAssocType == null) {
	    Object ae = asciter.next(); //MAssociationEnd
	    if (ModelFacade.getName(ae) != null
		&& name.equals(ModelFacade.getName(ae))) {
                    
		foundAssocType = ModelFacade.getType(ae);
	    } else if (ModelFacade.getName(ae) == null
		       || ModelFacade.getName(ae).equals("")) {
                           
		String oppositeName =
		    ModelFacade.getName(ModelFacade.getType(ae));
		if (oppositeName != null) {
                    
		    String lowerOppositeName =
			oppositeName.substring(0, 1).toLowerCase();
		    lowerOppositeName += oppositeName.substring(1);
		    if (lowerOppositeName.equals(name)) {
		        foundAssocType = ModelFacade.getType(ae);
		    }
		}
	    }
	    if (foundAssocType != null) {
		Object multiplicity = ModelFacade.getMultiplicity(ae);
		if (multiplicity != null
		    && (ModelFacade.getUpper(multiplicity) > 1
			|| ModelFacade.getUpper(multiplicity) 
                           == -1)) {
		    // to do: think about the condition of this if-statement
		    // ordered association end -> Sequence; otherwise -> Set
                    Object stereotype = null;
                    if (ModelFacade.getStereotypes(ae).size() > 0) {
                        stereotype = 
                            ModelFacade.getStereotypes(ae).iterator().next();
                    }
		    if (stereotype != null
			    && stereotype.toString() != null
			    && "ordered".equals(stereotype.toString())) {
			isSequence = true;
		    } else {
			isSet = true;
		    }
		}
	    }
	}

	if (foundAssocType != null && foundAttribType != null) {
	    throw new OclTypeException("cannot access feature " + name 
				       + " of classifier " + toString()
				       + " because both an attribute and "
				       + "an association end of this name "
				       + "where found");
	}

	Object foundType;
	if (foundAssocType == null) {
	    foundType = foundAttribType;
	} else {
	    foundType = foundAssocType;
	}

	if (foundType == null) {
	    throw new OclTypeException("attribute " + name
				       + " not found in classifier "
				       + toString());
	}

	Type result = getOclRepresentation(foundType);

	if (isSet) {
	    result =
		new tudresden.ocl.check.types.Collection(
		        tudresden.ocl.check.types.Collection.SET,
			result);
	}
	if (isSequence) {
	    result =
		new tudresden.ocl.check.types.Collection(
			tudresden.ocl.check.types.Collection.SEQUENCE,
			result);
	}

	return result;
    }

    /**
     * @see tudresden.ocl.check.types.Type2#navigateParameterizedQuery(
     *         java.lang.String, tudresden.ocl.check.types.Type[])
     */
    public Type navigateParameterizedQuery (String name, Type[] qualifiers)
	throws OclTypeException {
	return internalNavigateParameterized(name, qualifiers, true);
    }

    /**
     * @see tudresden.ocl.check.types.Type#navigateParameterized(
     *         java.lang.String, tudresden.ocl.check.types.Type[])
     */
    public Type navigateParameterized (String name, Type[] qualifiers)
	throws OclTypeException {
	return internalNavigateParameterized(name, qualifiers, false);
    }

    public Type internalNavigateParameterized(final String name,
                                              final Type[] params,
                                              boolean fCheckIsQuery)
	throws OclTypeException {
	if (classifier == null) {
	    throw new OclTypeException("attempting to access features of Void");
	}

	Type type = Basic.navigateAnyParameterized(name, params);
	if (type != null) {
	    return type;
	}

	Object foundOp = null; //MOperation
	java.util.Collection operations = ModelFacade.getOperations(classifier);
	Iterator iter = operations.iterator();
	while (iter.hasNext() && foundOp == null) {
	    Object op = iter.next();
	    if (operationMatchesCall(op, name, params)) {
		foundOp = op;
	    }
	}

	if (foundOp == null) {
	    throw new OclTypeException("operation " + name 
				       + " not found in classifier "
				       + toString()); 
	}

	if (fCheckIsQuery) {
	    /* Query checking added 05/21/01, sz9 */
	    if (!ModelFacade.isQuery(foundOp)) {
		throw new OclTypeException("Non-query operations cannot "
					   + "be used in OCL expressions. ("
					   + name + ")");
	    }
	}

	Object rp =
	    Model.getUmlHelper().getCore().getReturnParameter(foundOp);

	if (rp == null || ModelFacade.getType(rp) == null) {
	    LOG.warn("WARNING: supposing return type void!");
	    return new ArgoAny(null);
	}
	Object returnType = ModelFacade.getType(rp);

	return getOclRepresentation(returnType);
    }

    /**
     * @see tudresden.ocl.check.types.Type#conformsTo(tudresden.ocl.check.types.Type)
     */
    public boolean conformsTo(Type type) {
	if (type instanceof ArgoAny) {
	    ArgoAny other = (ArgoAny) type;
	    return equals(type)
		|| Model.getUmlHelper().getCore()
		    .getAllSupertypes(classifier).contains(other.classifier);
	} else {
	    return false;
	}
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object o) {
	ArgoAny any = null;
	if (o instanceof ArgoAny) {
	    any = (ArgoAny) o;
	    return (any.classifier == classifier);
	}
	return false;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
	if (classifier == null) {
	    return 0;
	}
	return classifier.hashCode();
    }

    /**
     * @see java.lang.Object#toString()
     */
    public String toString() {
	if (classifier == null) {
	    return "Void";
	}
	return ModelFacade.getName(classifier);
    }

    /**
     * @see tudresden.ocl.check.types.Type#hasState(java.lang.String)
     */
    public boolean hasState(String name) {
	LOG.warn("ArgoAny.hasState() has been called, but is "
		 + "not implemented yet!");
	return false;
    }

    protected Type getOclRepresentation(Object foundType) {
	Type result = null;

	if (ModelFacade.getName(foundType).equals("int")
	    || ModelFacade.getName(foundType).equals("Integer")) {
	    result = Basic.INTEGER;
	}

	if (ModelFacade.getName(foundType).equals("float")
	    || ModelFacade.getName(foundType).equals("double")) {
	    result = Basic.REAL;
	}

	if (ModelFacade.getName(foundType).equals("bool")
	    || ModelFacade.getName(foundType).equals("Boolean")
	    || ModelFacade.getName(foundType).equals("boolean")) {
	    result = Basic.BOOLEAN;
	}

	if (ModelFacade.getName(foundType).equals("String")) {
	    result = Basic.STRING;
	}

	if (result == null) {
	    result = new ArgoAny(foundType);
	}

	return result;
    }

    /**
     * @param operation The operation.
     * @param callName The name that we are trying to match.
     * @param callParams The parameters that we are trying to match.
     * @return <tt>true</tt> if the given Operation names and parameters
     *	       match the given name and parameters.
     */
    protected boolean operationMatchesCall(Object operation,
					   String callName,
					   Type[] callParams) {
	if (!callName.equals(ModelFacade.getName(operation))) {
	    return false;
	}

        Collection operationParameters = ModelFacade.getParameters(operation);
	if (!ModelFacade.isReturn(operationParameters.iterator().next())) {
	    LOG.warn(
                "ArgoFacade$ArgoAny expects the first operation parameter "
		+ "to be the return type; this isn't the case"
	    );
	}
	if (!(ModelFacade.isReturn(operationParameters.iterator().next())
	      && operationParameters.size() == (callParams.length + 1))) {
	    return false;
	}
	Iterator paramIter = operationParameters.iterator();
	paramIter.next(); // skip first parameter == return type
	int index = 0;
	while (paramIter.hasNext()) {
	    Object nextParam = paramIter.next();
	    Object paramType = ModelFacade.getType(nextParam); //MClassifier
	    Type operationParam = getOclRepresentation(paramType);
	    if (!callParams[index].conformsTo(operationParam)) {
		return false;
	    }
	    index++;
	}
	return true;
    }
}
