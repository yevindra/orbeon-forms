/**
 * Copyright (C) 2009 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.submission;

import org.dom4j.Document;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.xforms.ReadonlyXFormsInstance;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.action.actions.XFormsDeleteAction;
import org.orbeon.oxf.xforms.action.actions.XFormsInsertAction;
import org.orbeon.oxf.xforms.event.events.XFormsBindingExceptionEvent;
import org.orbeon.oxf.xforms.event.events.XFormsInsertEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import java.util.Collections;
import java.util.List;

/**
 * Handle replace="instance".
 */
public class InstanceReplacer extends BaseReplacer {

    public InstanceReplacer(XFormsModelSubmission submission, XFormsContainingDocument containingDocument) {
        super(submission, containingDocument);
    }

    public void replace(PipelineContext pipelineContext, ConnectionResult connectionResult, XFormsModelSubmission.SubmissionParameters p, XFormsModelSubmission.SecondPassParameters p2) {

        if (XMLUtils.isXMLMediatype(connectionResult.getResponseMediaType())) {
            // Handling of XML media type
            // Set new instance document to replace the one submitted

            final XFormsInstance replaceInstanceNoTargetref = submission.findReplaceInstanceNoTargetref(p.refInstance);
            if (replaceInstanceNoTargetref == null) {

                // Replacement instance or node was specified but not found
                //
                // Not sure what's the right thing to do with 1.1, but this could be done
                // as part of the model's static analysis if the instance value is not
                // obtained through AVT, and dynamically otherwise. However, in the dynamic
                // case, I think that this should be a (currently non-specified by XForms)
                // xforms-binding-error.

                submission.getXBLContainer(containingDocument).dispatchEvent(pipelineContext, new XFormsBindingExceptionEvent(submission));
            } else {

                final NodeInfo destinationNodeInfo = submission.evaluateTargetRef(pipelineContext,
                        p.xpathContext, replaceInstanceNoTargetref, p.submissionElementContextItem);

                if (destinationNodeInfo == null) {
                    // Throw target-error

                    // XForms 1.1: "If the processing of the targetref attribute fails,
                    // then submission processing ends after dispatching the event
                    // xforms-submit-error with an error-type of target-error."

                    throw new XFormsSubmissionException(submission, "targetref attribute doesn't point to an element for replace=\"instance\".", "processing targetref attribute",
                            new XFormsSubmitErrorEvent(pipelineContext, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, connectionResult));
                }

                // This is the instance which is effectively going to be updated
                final XFormsInstance updatedInstance = containingDocument.getInstanceForNode(destinationNodeInfo);
                if (updatedInstance == null) {
                    throw new XFormsSubmissionException(submission, "targetref attribute doesn't point to an element in an existing instance for replace=\"instance\".", "processing targetref attribute",
                            new XFormsSubmitErrorEvent(pipelineContext, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, connectionResult));
                }

                // Whether the destination node is the root element of an instance
                final boolean isDestinationRootElement = updatedInstance.getInstanceRootElementInfo().isSameNodeInfo(destinationNodeInfo);
                if (p2.resolvedXXFormsReadonly && !isDestinationRootElement) {
                    // Only support replacing the root element of an instance when using a shared instance
                    throw new XFormsSubmissionException(submission, "targetref attribute must point to instance root element when using read-only instance replacement.", "processing targetref attribute",
                            new XFormsSubmitErrorEvent(pipelineContext, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, connectionResult));
                }

                // Obtain root element to insert
                final NodeInfo newDocumentRootElement;
                final XFormsInstance newInstance;
                try {
                    // Create resulting instance whether entire instance is replaced or not, because this:
                    // 1. Wraps a Document within a DocumentInfo if needed
                    // 2. Performs text nodes adjustments if needed
                    if (!p2.resolvedXXFormsReadonly) {
                        // Resulting instance must not be read-only

                        // TODO: What about configuring validation? And what default to choose?
                        final Document resultingInstanceDocument
                                = TransformerUtils.readDom4j(connectionResult.getResponseInputStream(), connectionResult.resourceURI, p2.resolvedXXFormsHandleXInclude);

                        if (XFormsServer.logger.isDebugEnabled())
                            containingDocument.logDebug("submission", "replacing instance with mutable instance",
                                "instance", updatedInstance.getEffectiveId());

                        newInstance = new XFormsInstance(updatedInstance.getEffectiveModelId(), updatedInstance.getId(),
                                resultingInstanceDocument, connectionResult.resourceURI, p2.resolvedXXFormsUsername, p2.resolvedXXFormsPassword,
                                false, -1, updatedInstance.getValidation(), p2.resolvedXXFormsHandleXInclude);
                    } else {
                        // Resulting instance must be read-only

                        // TODO: What about configuring validation? And what default to choose?
                        // NOTE: isApplicationSharedHint is always false when get get here. isApplicationSharedHint="true" is handled above.
                        final DocumentInfo resultingInstanceDocument
                                = TransformerUtils.readTinyTree(connectionResult.getResponseInputStream(), connectionResult.resourceURI, p2.resolvedXXFormsHandleXInclude);

                        if (XFormsServer.logger.isDebugEnabled())
                            containingDocument.logDebug("submission", "replacing instance with read-only instance",
                                "instance", updatedInstance.getEffectiveId());

                        newInstance = new ReadonlyXFormsInstance(updatedInstance.getEffectiveModelId(), updatedInstance.getId(),
                                resultingInstanceDocument, connectionResult.resourceURI, p2.resolvedXXFormsUsername, p2.resolvedXXFormsPassword,
                                false, -1, updatedInstance.getValidation(), p2.resolvedXXFormsHandleXInclude);
                    }
                    newDocumentRootElement = newInstance.getInstanceRootElementInfo();
                } catch (Exception e) {
                    throw new XFormsSubmissionException(submission, e, "xforms:submission: exception while reading XML response.", "processing instance replacement",
                            new XFormsSubmitErrorEvent(pipelineContext, submission, XFormsSubmitErrorEvent.ErrorType.PARSE_ERROR, connectionResult));
                }

                // Perform insert/delete. This will dispatch xforms-insert/xforms-delete events.
                // "the replacement is performed by an XForms action that performs some
                // combination of node insertion and deletion operations that are
                // performed by the insert action (10.3 The insert Element) and the
                // delete action"

                if (isDestinationRootElement) {
                    // Optimized insertion for instance root element replacement

                    // Handle new instance and associated event markings
                    final XFormsModel replaceModel = newInstance.getModel(containingDocument);
                    replaceModel.handleUpdatedInstance(pipelineContext, newInstance, newDocumentRootElement);

                    // Dispatch xforms-delete event
                    // NOTE: Do NOT dispatch so we are compatible with the regular root element replacement
                    // (see below). In the future, we might want to dispatch this, especially if
                    // XFormsInsertAction dispatches xforms-delete when removing the root element
                    //updatedInstance.getXBLContainer(containingDocument).dispatchEvent(pipelineContext, new XFormsDeleteEvent(updatedInstance, Collections.singletonList(destinationNodeInfo), 1));

                    // Dispatch xforms-insert event
                    // NOTE: use the root node as insert location as it seems to make more sense than pointing to the earlier root element
                    newInstance.getXBLContainer(containingDocument).dispatchEvent(pipelineContext,
                        new XFormsInsertEvent(newInstance, Collections.singletonList((Item) newDocumentRootElement), null, newDocumentRootElement.getDocumentRoot(),
                                "after", null, null, true));

                } else {
                    // Generic insertion

                    final List<NodeInfo> destinationCollection = Collections.singletonList(destinationNodeInfo);

                    // Perform the insertion

                    // Insert before the target node, so that the position of the inserted node
                    // wrt its parent does not change after the target node is removed
                    final List insertedNode = XFormsInsertAction.doInsert(pipelineContext, containingDocument, "before",
                            destinationCollection, destinationNodeInfo.getParent(),
                            Collections.singletonList(newDocumentRootElement), 1, false, true);

                    if (!destinationNodeInfo.getParent().isSameNodeInfo(destinationNodeInfo.getDocumentRoot())) {
                        // The node to replace is NOT a root element

                        // Perform the deletion of the selected node
                        XFormsDeleteAction.doDelete(pipelineContext, containingDocument, destinationCollection, 1, true);
                    }

                    // Perform model instance update
                    // Handle new instance and associated event markings
                    // NOTE: The inserted node NodeWrapper.index might be out of date at this point because:
                    // * doInsert() dispatches an event which might itself change the instance
                    // * doDelete() does as well
                    // Does this mean that we should check that the node is still where it should be?
                    final XFormsModel updatedModel = updatedInstance.getModel(containingDocument);
                    updatedModel.handleUpdatedInstance(pipelineContext, updatedInstance, (NodeInfo) insertedNode.get(0));
                }

                // Dispatch xforms-submit-done
                dispatchSubmitDone(pipelineContext, connectionResult);
            }
        } else {
            // Other media type
            throw new XFormsSubmissionException(submission, "Body received with non-XML media type for replace=\"instance\": " + connectionResult.getResponseMediaType(), "processing instance replacement",
                    new XFormsSubmitErrorEvent(pipelineContext, submission, XFormsSubmitErrorEvent.ErrorType.RESOURCE_ERROR, connectionResult));
        }
    }
}