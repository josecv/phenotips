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
package org.phenotips.studies.family.script2;

import org.phenotips.data.Patient;
import org.phenotips.data.PatientRepository;
import org.phenotips.studies.family.Family;
import org.phenotips.studies.family.FamilyRepository;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;

/**
 * Script service for working with families.
 *
 * @version $Id$
 * @since 1.2RC1
 */
@Component
@Singleton
@Named("families")
public class FamilyScriptService2
{
    @Inject
    private Logger logger;

    @Inject
    private FamilyRepository familyRepository;

    @Inject
    private PatientRepository patientRepository;

    /**
     * Either creates a new family, or gets the existing one if a patient belongs to a family. TODO - change Name to
     * getFamily
     *
     * @param patientId id of the patient to use when searching for or creating a new family
     * @return reference to the family document. Can be {@link null}
     */
    public DocumentReference createFamily(String patientId)
    {
        Patient patient = this.patientRepository.getPatientById(patientId);
        if (patient == null) {
            this.logger.error("Could not find patient with id [{}]", patientId);
            return null;
        }

        Family xwikiFamily = this.familyRepository.getFamilyForPatient(patient);
        if (xwikiFamily == null) {
            this.logger.debug("No family for patient [{}]. Creating new.", patientId);
            xwikiFamily = this.familyRepository.createFamily();
            xwikiFamily.addMember(patient);
        }

        return xwikiFamily.getDocumentReference();
    }

    /**
     * Creates an empty family
     *
     * @return DocumentReference to the newly created family
     */
    public DocumentReference createFamily()
    {
        Family family = this.familyRepository.createFamily();
        return family.getDocumentReference();
    }

    /**
     * Returns a reference to a patient's family, or null if doesn't exist. TODO - change call to pass patientId and not
     * XWikiDocument ($doc.getDocument() -> $doc.getDocument().getName()). Also change name to getFamilyForPatientId
     *
     * @param patientId id of the patient
     * @return DocumentReference to the family, or null if doesn't exist
     */
    public DocumentReference getPatientsFamily(String patientId)
    {
        Patient patient = this.patientRepository.getPatientById(patientId);
        Family family = this.familyRepository.getFamilyForPatient(patient);
        if (family == null) {
            return null;
        }
        return family.getDocumentReference();
    }
}
