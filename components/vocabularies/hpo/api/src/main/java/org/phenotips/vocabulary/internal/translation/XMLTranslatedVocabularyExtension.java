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
package org.phenotips.vocabulary.internal.translation;

import org.phenotips.vocabulary.VocabularyExtension;
import org.phenotips.vocabulary.VocabularyInputTerm;

import org.xwiki.component.annotation.Component;
import org.xwiki.localization.LocalizationContext;

import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Implements {@link VocabularyExtension} to provide translation services.
 * Works with the xml files up at https://github.com/Human-Phenotype-Ontology/HPO-translations
 *
 * @version $Id$
 */
@Component
@Singleton
public class XMLTranslatedVocabularyExtension implements VocabularyExtension
{
    /**
     * The format for the file containing the translation - where the first string is the ontology
     * name and the second is the language code.
     */
    private static final String TRANSLATION_XML_FORMAT = "%s_%s.xliff";

    /**
     * The initial size of the map containing translations.
     */
    private static final int INITIAL_MAP_SIZE = 16348;

    /* TODO: I don't like having these field names down here, I'd rather they were
     * somehow set by the vocabulary input term, but that'd require coupling the VocabularyInputTerm
     * to the concept of a language which makes me uncomfortable too */

    /**
     * The format for the name field.
     */
    private static final String NAME_FORMAT = "name_%s";

    /**
     * The format for the definition field.
     */
    private static final String DEF_FORMAT = "def_%s";

    /**
     * The format for the synonym field.
     */
    private static final String SYNONYM_FORMAT = "synonym_%s";

    /**
     * The xml reader.
     */
    private XMLReader reader;

    /**
     * The entity handler to process the xml file element by element.
     */
    private EntityHandler handler;

    /**
     * The current language. Will be set when we start indexing so that
     * the component supports dynamically switching without restarting phenotips.
     */
    private String lang;

    /**
     * The logger.
     */
    @Inject
    private Logger logger;

    /**
     * The localization context.
     */
    @Inject
    private LocalizationContext localizationContext;

    @Override
    public Collection<String> getSupportedVocabularies()
    {
        Collection<String> retval = new ArrayList<>();
        retval.add("hpo");
        return retval;
    }

    @Override
    public void indexingStarted(String vocabulary)
    {
        try {
            reader = XMLReaderFactory.createXMLReader();
            handler = new EntityHandler();
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            lang = localizationContext.getCurrentLocale().getLanguage();
            String xml = String.format(TRANSLATION_XML_FORMAT, vocabulary, lang);
            InputStream inStream = this.getClass().getResourceAsStream(xml);
            if (inStream == null) {
                /* parse will strangely throw a malformed url exception if this is null, which
                 * is impossible to distinguish from an actual malformed url exception,
                 * so check here and prevent going forward if there's no translation */
                logger.warn(String.format("Could not find resource %s", xml));
                return;
            }
            logger.debug(String.format("Will parse %s", xml));
            reader.parse(new InputSource(inStream));
        } catch (SAXException | IOException e) {
            throw new RuntimeException("indexingStarted exception", e);
        }
    }

    @Override
    public void indexingEnded(String vocabulary)
    {

    }

    @Override
    public void extendTerm(VocabularyInputTerm term, String vocabulary)
    {
        String id = term.getId();
        Map<String, String> translated = handler.translations.get(id);
        if (translated == null) {
            return;
        }
        String label = translated.get("label");
        String definition = translated.get("definition");
        if (label != null) {
            term.set(String.format(NAME_FORMAT, lang), label);
        }
        if (definition != null) {
            term.set(String.format(DEF_FORMAT, lang), definition);
        }
        /* TODO Else clauses that dynamically machine-translate the missing stuff (or get it from
         * a cache so we don't spend our lives translating).
         */
    }

    /**
     * The entity handler to read through the xml file.
     *
     * @version $Id$
     */
    private static class EntityHandler extends DefaultHandler
    {
        /**
         * The pattern to parse ids of translation units.
         */
        private static final Pattern ID_PATTERN = Pattern.compile("(HP_\\d+)_(.*)");

        /**
         * The name of the target tag.
         */
        private static final String TARGET = "target";

        /**
         * The name of the id attribute.
         */
        private static final String ID = "id";

        /**
         * The name of the translation unit tag.
         */
        private static final String TRANSLATION_UNIT = "trans-unit";

        /**
         * The current hpo term id being looked at.
         */
        private String currentTerm;

        /**
         * The current attribute being looked at.
         */
        private String currentAttr;

        /**
         * Whether we are looking at the target tag of a translation unit.
         */
        private boolean inTarget;

        /**
         * A map of {HPO_ID : {attribute : translation}} to be used when extending
         * terms.
         */
        private Map<String, Map<String, String>> translations = new HashMap<>(INITIAL_MAP_SIZE);

        @Override
        public void startElement(String uri, String name, String qName, Attributes attrs)
        {
            if (TRANSLATION_UNIT.equals(name)) {
                Matcher m = ID_PATTERN.matcher(attrs.getValue(ID));
                m.find();
                if (m.matches()) {
                    currentTerm = m.group(1).replace("_", ":");
                    currentAttr = m.group(2);
                }
            } else if (TARGET.equals(name) && currentTerm != null) {
                inTarget = true;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length)
        {
            if (inTarget) {
                Map<String, String> map = translations.get(currentTerm);
                if (map == null) {
                    map = new HashMap<>(4);
                    translations.put(currentTerm, map);
                }
                StringBuilder b = new StringBuilder();
                b.append(ch, start, length);
                map.put(currentAttr, b.toString());
            }
        }

        @Override
        public void endElement(String uri, String name, String qName)
        {
            if (TRANSLATION_UNIT.equals(name)) {
                currentTerm = null;
                currentAttr = null;
            } else if (TARGET.equals(name) && inTarget) {
                inTarget = false;
            }
        }
    }
}
