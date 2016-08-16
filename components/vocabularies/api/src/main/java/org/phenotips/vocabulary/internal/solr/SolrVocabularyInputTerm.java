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
package org.phenotips.vocabulary.internal.solr;

import org.phenotips.vocabulary.Vocabulary;
import org.phenotips.vocabulary.VocabularyInputTerm;
import org.phenotips.vocabulary.VocabularyTerm;

import java.util.Set;

import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;

/**
 * Implementation for {@link VocabularyInputTerm} based on a Solr document.
 *
 * @version $Id$
 */
public class SolrVocabularyInputTerm extends SolrVocabularyTerm implements VocabularyInputTerm
{
    /**
     * The solr input document.
     */
    private SolrInputDocument doc;

    /**
     * Constructor.
     *
     * @param doc the solr document representing the term
     * @param ontology the owner ontology
     */
    public SolrVocabularyInputTerm(SolrInputDocument doc, Vocabulary ontology)
    {
        super(doc, ontology);
        this.doc = doc;
    }

    @Override
    public VocabularyInputTerm setId(String id)
    {
        if (doc != null) {
            doc.setField(ID, id);
        }
        return this;
    }

    @Override
    public VocabularyInputTerm setName(String name)
    {
        if (doc != null) {
            doc.setField(NAME, name);
        }
        return this;
    }

    @Override
    public VocabularyInputTerm setDescription(String description)
    {
        if (doc != null) {
            doc.setField(DEF, description);
        }
        return this;
    }

    @Override
    public VocabularyInputTerm setParents(Set<VocabularyTerm> parents)
    {
        if (doc != null) {
            SolrInputField field = new SolrInputField(IS_A);
            for (VocabularyTerm parent : parents) {
                field.addValue(parent.getId(), 1.0f);
            }
            doc.put(IS_A, field);
        }
        return this;
    }

    @Override
    public VocabularyInputTerm set(String key, Object value)
    {
        if (doc != null) {
            doc.setField(key, value);
        }
        return this;
    }

    @Override
    public VocabularyInputTerm append(String key, Object value)
    {
        if (doc != null) {
            doc.addField(key, value);
        }
        return this;
    }

    @Override
    public Set<VocabularyTerm> getParents()
    {
        /* We have to override this because the parents might be re-set */
        return new LazySolrTermSet(getValues(IS_A), ontology);
    }

    @Override
    public Set<VocabularyTerm> getAncestors()
    {
        return new LazySolrTermSet(getValues(TERM_CATEGORY), ontology);
    }

    @Override
    public Set<VocabularyTerm> getAncestorsAndSelf()
    {
        return new LazySolrTermSet(getAncestorsAndSelfTermSet(), ontology);
    }
}
