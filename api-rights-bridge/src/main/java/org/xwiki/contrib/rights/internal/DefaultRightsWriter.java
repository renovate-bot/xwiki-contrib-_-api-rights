/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.rights.internal;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.collections4.CollectionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.rights.RulesObjectWriter;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.ReadableSecurityRule;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.internal.mandatory.XWikiGlobalRightsDocumentInitializer;
import com.xpn.xwiki.internal.mandatory.XWikiRightsDocumentInitializer;

/**
 * @version $Id$
 */
@Component
@Singleton
public class DefaultRightsWriter extends AbstractRightsWriter
{
    static final String XWIKI_SPACE = XWiki.SYSTEM_SPACE;

    static final EntityReference XWIKI_RIGHTS_CLASS = XWikiRightsDocumentInitializer.CLASS_REFERENCE;

    static final EntityReference XWIKI_GLOBAL_RIGHTS_CLASS = XWikiGlobalRightsDocumentInitializer.CLASS_REFERENCE;

    @Inject
    @Named("recycling")
    private RulesObjectWriter objectsWriter;

    @Inject
    private ComponentManager cm;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Override
    public void saveRules(List<ReadableSecurityRule> rules, EntityReference reference, String persistenceStrategyName)
        throws XWikiException, UnsupportedOperationException, ComponentLookupException
    {
        RulesObjectWriter writer = cm.getInstance(RulesObjectWriter.class, persistenceStrategyName);
        saveRules(rules, reference, writer);
    }

    /**
     * One rule will correspond to one right object.
     *
     * @see org.xwiki.contrib.rights.RightsWriter#saveRules(java.util.List, org.xwiki.model.reference.EntityReference)
     */
    @Override
    public void saveRules(List<ReadableSecurityRule> rules, EntityReference reference)
        throws XWikiException, UnsupportedOperationException
    {
        saveRules(rules, reference, objectsWriter);
    }

    private void saveRules(List<ReadableSecurityRule> rules, EntityReference reference, RulesObjectWriter rulesWriter)
        throws XWikiException, UnsupportedOperationException
    {
        // By deleting the objects, the object number will continue from the number of the deleted object.
        if (reference != null && rules != null) {
            DocumentReference rightsStorageDocReference;
            EntityReference rightsClassReference;
            boolean storageDocIsPrefsDoc = false;
            switch (reference.getType()) {
                case WIKI:
                    rightsStorageDocReference = new DocumentReference(RulesObjectWriter.XWIKI_PREFERENCES,
                        new SpaceReference(XWIKI_SPACE, new WikiReference(reference)));
                    rightsClassReference = XWIKI_GLOBAL_RIGHTS_CLASS;
                    storageDocIsPrefsDoc = true;
                    break;
                case SPACE:
                    rightsStorageDocReference = new DocumentReference(RulesObjectWriter.XWIKI_WEB_PREFERENCES,
                        new SpaceReference(reference));
                    rightsClassReference = XWIKI_GLOBAL_RIGHTS_CLASS;
                    storageDocIsPrefsDoc = true;
                    break;
                case DOCUMENT:
                    // The current reference corresponds to a terminal page.
                    rightsStorageDocReference = new DocumentReference(reference);
                    rightsClassReference = XWIKI_RIGHTS_CLASS;
                    break;
                default:
                    throw new UnsupportedOperationException("Could not set rights for the given reference.");
            }
            // get document to perform changes on
            XWikiContext context = getXContext();
            XWikiDocument rightsStorageDoc = getXWiki().getDocument(rightsStorageDocReference, context).clone();

            // write objects according to the chosen strategy
            rulesWriter.persistRulesToObjects(rules, rightsStorageDoc, rightsClassReference, context);

            // In the end, save the document
            rightsStorageDoc.setAuthorReference(context.getUserReference());

            // if the document is newly created now and it's a storage preferences document, initialize it as hidden
            // (all preferences documents are hidden).
            // Note: if the caller is explicitly saving rights on a WebPreferences document with a direct document
            // reference to it, the document will not be set as hidden, it's the caller's responsibility; this rule here
            // only covers for the case when the prefs document is created for the storage needs.
            if (rightsStorageDoc.isNew() && storageDocIsPrefsDoc) {
                rightsStorageDoc.setHidden(true);
            }
            // Save document only if exist or if there are some rules
            if (!(CollectionUtils.isEmpty(rules) && rightsStorageDoc.isNew())) {
                getXWiki().saveDocument(rightsStorageDoc, context);
            }
        }
    }

    /**
     * @return the xcontext
     */
    private XWikiContext getXContext()
    {
        return xcontextProvider.get();
    }

    /**
     * @return the XWiki object
     */
    private XWiki getXWiki()
    {
        return getXContext().getWiki();
    }
}
