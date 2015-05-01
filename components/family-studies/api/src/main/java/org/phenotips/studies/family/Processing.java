package org.phenotips.studies.family;

import org.phenotips.studies.family.internal.StatusResponse;

import org.xwiki.component.annotation.Role;
import org.xwiki.query.QueryException;

import javax.naming.NamingException;

import com.xpn.xwiki.XWikiException;

import net.sf.json.JSONObject;

@Role
public interface Processing
{
    /** The name under which the linked patient id resides under in the JSON generated by the pedigree. */
    String PATIENT_LINK_JSON_KEY = "phenotipsId";

    /**
     * Performs several operations on the passed in data, and eventually saves it into appropriate documents.
     *
     * @param anchorId could be a family id or a patient id. If a patient does not belong to a family, there is no
     * processing of the pedigree, and the pedigree is simply saved to that patient record. If the patient does belong
     * to a family, or a family id is passed in as the `anchorId`, there is processing of the pedigree, which is then
     * saved to all patient records that belong to the family and the family document itself.
     * @param json (data) part of the pedigree JSON
     * @param image svg part of the pedigree JSON
     * @return {@link StatusResponse} with one of many possible statuses
     */
    StatusResponse processPatientPedigree(String anchorId, JSONObject json, String image)
        throws XWikiException, NamingException, QueryException;
}
