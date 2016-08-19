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

import org.phenotips.vocabulary.MachineTranslator;
import org.phenotips.vocabulary.VocabularyInputTerm;
import org.phenotips.vocabulary.VocabularyTerm;

import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.environment.Environment;
import org.xwiki.localization.LocalizationContext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;

import org.slf4j.Logger;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

/**
 * Base class for any machine translator implementations.
 * FIXME THIS DOESN'T DEAL OKAY WITH MULTIVALUEDS
 *
 * @version $Id$
 */
public abstract class AbstractMachineTranslator implements MachineTranslator, Initializable
{
    /**
     * The name of the directory containing the translation files.
     */
    protected static final String HOME_DIR_NAME = "vocabulary_translations";

    /**
     * The current environment.
     */
    @Inject
    private Environment environment;

    /**
     * The current localization context.
     */
    @Inject
    private LocalizationContext localizationContext;

    /**
     * Our logger.
     */
    @Inject
    private Logger logger;

    /**
     * The home directory.
     */
    private File home;

    /**
     * The loaded translations.
     */
    private Map<String, XLiffFile> translations = new HashMap<>();

    /**
     * The xml mapper.
     */
    private XmlMapper mapper = new XmlMapper();

    /**
     * The current language.
     */
    private String lang;

    @Override
    public void initialize() throws InitializationException
    {
        home = environment.getPermanentDirectory();
        home = new File(home, HOME_DIR_NAME);
        home = new File(home, getIdentifier());
        if (!home.exists()) {
            home.mkdirs();
            for (String vocabulary : getSupportedVocabularies()) {
                for (String language : getSupportedLanguages()) {
                    String name = getFileName(vocabulary, language);
                    InputStream resource = this.getClass().getResourceAsStream(name);
                    try {
                        OutputStream out = new FileOutputStream(new File(home, name));
                        IOUtils.copy(resource, out);
                        out.close();
                        resource.close();
                    } catch (IOException e) {
                        /* This is bad, prevent the component from working at all */
                        throw new InitializationException(e.getMessage(), e);
                    }
                }
            }
        }
    }

    /**
     * Get the name of the translation file for the vocabulary and language given.
     *
     * @param vocabulary the vocabulary
     * @param language the language
     * @return the translation file name
     */
    protected String getFileName(String vocabulary, String language)
    {
        return String.format("%s_%s_%s.xliff", getIdentifier(), vocabulary, language);
    }

    /**
     * Get an identifier for this machine translator.
     *
     * @return the identifier
     */
    protected abstract String getIdentifier();

    /**
     * Get the home directory for the machine translating component.
     *
     * @return the home directory
     */
    protected File getHomeDir()
    {
        return home;
    }

    @Override
    public boolean loadVocabulary(String vocabulary)
    {
        lang = localizationContext.getCurrentLocale().getLanguage();
        File file = new File(home, getFileName(vocabulary, lang));
        try {
            XLiffFile xliff = mapper.readValue(file, XLiffFile.class);
            translations.put(vocabulary, xliff);
        } catch (IOException e) {
            logger.error(String.format("Could not load %s: %s", file.toString(), e.getMessage()));
            return false;
        }
        return true;
    }

    @Override
    public boolean unloadVocabulary(String vocabulary)
    {
        XLiffFile xliff = translations.remove(vocabulary);
        File file = new File(home, getFileName(vocabulary, lang));
        try {
            mapper.writeValue(file, xliff);
        } catch (IOException e) {
            logger.error(String.format("Threw on writing %s: %s", file.toString(),
                        e.getMessage()));
            return false;
        }
        return true;
    }

    @Override
    public long getMissingCharacters(String vocabulary, VocabularyTerm term,
            Collection<String> fields)
    {
        long count = 0;
        XLiffFile xliff = getXliff(vocabulary);
        for (String field : fields) {
            if (xliff.getString(term.getId(), field) == null) {
                Object o = term.get(field);
                if (o instanceof String) {
                    count += ((String) o).length();
                } else if (o instanceof Iterable) {
                    Iterable<Object> objects = (Iterable) o;
                    for (Object inner : objects) {
                        if (inner instanceof String) {
                            count += ((String) inner).length();
                        }
                    }
                }
            }
        }
        return count;
    }

    @Override
    public long translate(String vocabulary, VocabularyInputTerm term, Collection<String> fields)
    {
        long count = 0;
        XLiffFile xliff = getXliff(vocabulary);
        for (String field : fields) {
            String translatedField = field + "_" + lang;
            String there = xliff.getString(term.getId(), field);
            if (there != null) {
                term.set(translatedField, there);
            } else {
                Object o = term.get(field);
                if (o instanceof String) {
                    String string = (String) o;
                    count += string.length();
                    String result = doTranslate(string);
                    term.set(translatedField, result);
                    xliff.setString(term.getId(), field, string, result);
                } else if (o instanceof Iterable) {
                    /* Temporary while we implement this, since having a bad implementation is
                     * gonna do more harm than good. */
                    throw new UnsupportedOperationException("Cannot translate multivalued fields yet");
                }
            }
        }
        return count;
    }

    /**
     * Actually run the input given through a machine translation.
     *
     * @param input the input
     * @return the translated string
     */
    protected abstract String doTranslate(String input);

    /**
     * Get the XLiffFile object associated with the vocabulary given, throw if it isn't there.
     *
     * @param vocabulary the vocabulary to get an xliff for
     * @return the xliff
     * @throws IllegalStateException if the xliff file has not been read
     */
    protected XLiffFile getXliff(String vocabulary)
    {
        XLiffFile xliff = translations.get(vocabulary);
        if (xliff == null) {
            throw new IllegalStateException(String.format("Vocabulary %s never initialized",
                        vocabulary));
        }
        return xliff;
    }
}
