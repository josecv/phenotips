/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */
package org.phenotips.studies.family.internal;

import org.phenotips.components.ComponentManagerRegistry;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;
import org.phenotips.studies.family.Family;
import org.phenotips.studies.family.Pedigree;
import org.phenotips.studies.family.internal.export.PhenotipsFamilyExport;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.context.Execution;
import org.xwiki.model.reference.DocumentReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.BaseStringProperty;
import com.xpn.xwiki.objects.ListProperty;
import com.xpn.xwiki.objects.StringProperty;

/**
 * XWiki implementation of Family.
 *
 * @version $Id$
 */
public class PhenotipsFamily implements Family
{
    private static final String WARNING = "warning";

    private static final String FAMILY_MEMBERS_FIELD = "members";

    private static PatientRepository patientRepository;

    private static PhenotipsFamilyPermissions familyPermissions;

    private static PhenotipsFamilyExport familyExport;

    @Inject
    private Logger logger;

    private XWikiDocument familyDocument;

    static {
        try {
            PhenotipsFamily.patientRepository =
                ComponentManagerRegistry.getContextComponentManager().getInstance(PatientRepository.class);
            PhenotipsFamily.familyPermissions =
                ComponentManagerRegistry.getContextComponentManager().getInstance(PhenotipsFamilyPermissions.class);
            PhenotipsFamily.familyExport =
                ComponentManagerRegistry.getContextComponentManager().getInstance(PhenotipsFamilyExport.class);

        } catch (ComponentLookupException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param familyDocument not-null document associated with the family
     */
    public PhenotipsFamily(XWikiDocument familyDocument)
    {
        this.familyDocument = familyDocument;
    }

    @Override
    public String getId()
    {
        return this.familyDocument.getDocumentReference().getName();
    }

    @Override
    public DocumentReference getDocumentReference()
    {
        return this.familyDocument.getDocumentReference();
    }

    @Override
    public List<String> getMembersIds()
    {
        BaseObject familyObject = this.familyDocument.getXObject(CLASS_REFERENCE);
        if (familyObject == null) {
            return new LinkedList<String>();
        }

        ListProperty xwikiRelativesList;
        try {
            xwikiRelativesList = (ListProperty) familyObject.get(FAMILY_MEMBERS_FIELD);
        } catch (XWikiException e) {
            this.logger.error("error reading family members: {}", e);
            return null;
        }
        if (xwikiRelativesList == null) {
            return Collections.emptyList();
        }
        return xwikiRelativesList.getList();
    }

    @Override
    public List<Patient> getMembers()
    {
        List<String> memberIds = this.getMembersIds();
        List<Patient> members = new ArrayList<>(memberIds.size());
        for (String memberId : memberIds) {
            Patient patient = PhenotipsFamily.patientRepository.getPatientById(memberId);
            members.add(patient);
        }
        return members;
    }

    @Override
    public synchronized boolean addMember(Patient patient)
    {
        if (patient == null) {
            this.logger.error("Can not add NULL patient to family [{}]", this.getId());
            return false;
        }

        String patientId = patient.getId();

        XWikiContext context = getXContext();
        XWiki wiki = context.getWiki();
        DocumentReference patientReference = patient.getDocument();
        XWikiDocument patientDocument;
        try {
            patientDocument = wiki.getDocument(patientReference, context);
        } catch (XWikiException e) {
            this.logger.error("Could not add patient [{}] to family. Error getting patient document: [{}]",
                patientId, e.getMessage());
            return false;
        }
        String patientAsString = patientReference.getName();

        // Add member to Xwiki family
        List<String> members = getMembersIds();
        if (!members.contains(patientAsString)) {
            members.add(patientAsString);
        } else {
            this.logger.info("Patient [{}] already a member of the same family, not adding", patientId);
            return false;
        }
        BaseObject familyObject = this.familyDocument.getXObject(Family.CLASS_REFERENCE);
        familyObject.set(FAMILY_MEMBERS_FIELD, members, context);

        String lastName = patient.<String>getData("patientName").get("last_name");
        if (this.getExternalId().isEmpty() && !lastName.isEmpty()) {
            familyObject.set("external_id", lastName, context);
        }

        PhenotipsFamily.familyPermissions.setFamilyPermissionsFromPatient(this.familyDocument, patientDocument);

        try {
            PhenotipsFamilyRepository.setFamilyReference(patientDocument, this.familyDocument, context);
        } catch (XWikiException e) {
            this.logger.error("Could not add patient [{}] to family. Error setting family reference: [{}]",
                patientId, e.getMessage());
            return false;
        }

        this.updatePermissions();

        if (!savePatientDocument(patientDocument, "added to family " + this.getId(), "adding to a family", context)
            || !saveFamilyDocument("added " + patientId + " to the family", "adding patient " + patientId, context)) {
            return false;
        }

        return true;
    }

    @Override
    public synchronized boolean removeMember(Patient patient)
    {
        if (patient == null) {
            return false;
        }

        String patientId = patient.getId();

        XWikiContext context = getXContext();
        XWiki wiki = context.getWiki();
        XWikiDocument patientDocument;
        try {
            patientDocument = wiki.getDocument(patient.getDocument(), context);
        } catch (XWikiException e) {
            this.logger.error("Error getting patient document. Patient id: [{}], error: [{}]",
                patientId, e.getMessage());
            return false;
        }

        // Remove family reference from patient
        boolean removed = PhenotipsFamilyRepository.removeFamilyReference(patientDocument);
        if (!removed) {
            this.logger.info("Family reference not removed from patient. Returning from removeMember()");
            return false;
        }

        // Remove patient from family's members list
        List<String> members = getMembersIds();
        String patientAsString = patient.getDocument().getName();
        if (!members.contains(patientAsString)) {
            this.logger.error("Patient has family reference but family doesn't have patient as member. "
                + "patientId: [{}], familyId: [{}]", patientId, this.getId());
        } else {
            members.remove(patientAsString);
        }
        BaseObject familyObject = this.familyDocument.getXObject(Family.CLASS_REFERENCE);
        familyObject.set(FAMILY_MEMBERS_FIELD, members, context);

        this.updatePermissions();

        // Remove patient from the pedigree
        Pedigree pedigree = getPedigree();
        if (pedigree != null) {
            pedigree.removeLink(patientId);
            if (!setPedigreeObject(pedigree, false)) {
                this.logger.error("Could not remove patient [{}] from pedigree for family [{}]",
                    patientId, this.getId());
                return false;
            }
        }

        if (!savePatientDocument(patientDocument, "removed from family", "removing from family", context)
            || !saveFamilyDocument("removed " + patientId + " from the family", "removing a family member", context)) {
            return false;
        }

        return true;
    }

    private boolean savePatientDocument(XWikiDocument patientDocument, String documentHistoryComment,
        String logActionDescription, XWikiContext context)
    {
        try {
            context.getWiki().saveDocument(patientDocument, documentHistoryComment, context);
        } catch (XWikiException e) {
            this.logger.error("Error saving patient [{}] document after {}: [{}]",
                patientDocument.getId(), logActionDescription, e.getMessage());
            return false;
        }
        return true;
    }

    private boolean saveFamilyDocument(String documentHistoryComment, String logActionDescription, XWikiContext context)
    {
        try {
            context.getWiki().saveDocument(this.familyDocument, documentHistoryComment, context);
        } catch (XWikiException e) {
            this.logger.error("Error saving family [{}] document after {}: [{}]",
                this.getId(), logActionDescription, e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    public boolean isMember(Patient patient)
    {
        List<String> members = getMembersIds();
        if (members == null) {
            return false;
        }
        String patientId = patient.getDocument().getName();
        return members.contains(patientId);
    }

    @Override
    public JSONObject toJSON()
    {
        return PhenotipsFamily.familyExport.toJSON(this);
    }

    @Override
    public Map<String, Map<String, String>> getMedicalReports()
    {
        Map<String, Map<String, String>> allFamilyLinks = new HashMap<>();

        for (Patient patient : getMembers()) {
            allFamilyLinks.put(patient.getId(), PhenotipsFamily.familyExport.getMedicalReports(patient));
        }
        return allFamilyLinks;
    }

    @Override
    public String getExternalId()
    {
        BaseObject familyObject = this.familyDocument.getXObject(Family.CLASS_REFERENCE);
        StringProperty externalId = null;
        String externalIdString = "";
        try {
            externalId = (StringProperty) familyObject.get("external_id");
            if (externalId != null) {
                externalIdString = externalId.getValue();
            }
        } catch (XWikiException e) {
            this.logger.error("Error reading external id of family [{}]: [{}]", getId(), e.getMessage());
        }
        return externalIdString;
    }

    @Override
    public String getURL(String actions)
    {
        return this.familyDocument.getURL(actions, getXContext());
    }

    private XWikiContext getXContext()
    {
        Execution execution = null;
        try {
            execution = ComponentManagerRegistry.getContextComponentManager().getInstance(Execution.class);
        } catch (ComponentLookupException ex) {
            // Should not happen
            return null;
        }
        XWikiContext context = (XWikiContext) execution.getContext().getProperty("xwikicontext");
        return context;
    }

    /*
     * Some pedigrees may contain sensitive information, which should be displayed on every edit of the pedigree. The
     * function returns a warning to display, or empty string
     */
    @Override
    public String getWarningMessage()
    {
        BaseObject familyObject = this.familyDocument.getXObject(Family.CLASS_REFERENCE);
        if (familyObject.getIntValue(WARNING) == 0) {
            return "";
        } else {
            return familyObject.getStringValue("warning_message");
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof PhenotipsFamily)) {
            return false;
        }

        PhenotipsFamily xobj = (PhenotipsFamily) obj;
        return (this.getId().equals(xobj.getId()));
    }

    @Override
    public int hashCode()
    {
        return this.getId().hashCode();
    }

    @Override
    public Pedigree getPedigree()
    {
        BaseObject pedigreeObj = this.familyDocument.getXObject(Pedigree.CLASS_REFERENCE);
        if (pedigreeObj != null) {
            BaseStringProperty data = null;
            BaseStringProperty image = null;

            try {
                data = (BaseStringProperty) pedigreeObj.get(Pedigree.DATA);
                image = (BaseStringProperty) pedigreeObj.get(Pedigree.IMAGE);

                if (StringUtils.isNotBlank(data.toText())) {
                    return new DefaultPedigree(new JSONObject(data.toText()), image.toText());
                }
            } catch (XWikiException e) {
                this.logger.error("Error reading data from pedigree. {}", e.getMessage());
            } catch (IllegalArgumentException e) {
                this.logger.error("Incorrect pedigree data. {}", e.getMessage());
            }
        }
        return null;
    }

    @Override
    public boolean setPedigree(Pedigree pedigree)
    {
        return setPedigreeObject(pedigree, true);
    }

    private boolean setPedigreeObject(Pedigree pedigree, boolean saveXwikiDocument)
    {
        if (pedigree == null) {
            this.logger.error("Can not set NULL pedigree for family [{}]", this.getId());
            return false;
        }

        XWikiContext context = getXContext();
        XWiki wiki = context.getWiki();

        BaseObject pedigreeObject = this.familyDocument.getXObject(Pedigree.CLASS_REFERENCE);
        pedigreeObject.set(Pedigree.IMAGE, pedigree.getImage(null), context);
        pedigreeObject.set(Pedigree.DATA, pedigree.getData().toString(), context);

        if (saveXwikiDocument) {
            try {
                wiki.saveDocument(this.familyDocument, "updated pedigree", context);
            } catch (XWikiException e) {
                this.logger.error("Error saving pedigree for family [{}]: {}", this.getId(), e.getMessage());
                return false;
            }
        }

        return true;
    }

    @Override
    public void updatePermissions()
    {
        PhenotipsFamily.familyPermissions.updatePermissions(this, this.familyDocument);
    }

    @Override
    public synchronized boolean deleteFamily(boolean deleteAllMembers)
    {
        XWikiContext context = getXContext();
        XWiki xwiki = context.getWiki();
        try {
            for (Patient patient : getMembers()) {
                removeMember(patient);
                if (deleteAllMembers) {
                    if (!PhenotipsFamily.patientRepository.deletePatient(patient.getId())) {
                        return false;
                    }
                }
            }
            xwiki.deleteDocument(xwiki.getDocument(this.familyDocument, context), context);
        } catch (XWikiException ex) {
            this.logger.error("Failed to delete family document [{}]: {}", this.getId(), ex.getMessage());
            return false;
        }
        return true;
    }
}
