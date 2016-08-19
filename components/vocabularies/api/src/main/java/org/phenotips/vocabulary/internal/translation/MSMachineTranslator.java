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

import org.xwiki.component.annotation.Component;

import java.util.Collection;
import java.util.HashSet;

import javax.inject.Singleton;

/**
 * Implements the {@link MachineTranslator} interface to provide translation services
 * through microsoft translate.
 *
 * @version $Id$
 */
@Component
@Singleton
public class MSMachineTranslator extends AbstractMachineTranslator
{
    /**
     * The supported languages.
     */
    private static final Collection<String> SUPPORTED_LANGUAGES;

    /**
     * The supported vocabularies.
     */
    private static final Collection<String> SUPPORTED_VOCABULARIES;

    static {
        SUPPORTED_LANGUAGES = new HashSet<>();
        SUPPORTED_LANGUAGES.add("es");
        SUPPORTED_VOCABULARIES = new HashSet<>();
        SUPPORTED_VOCABULARIES.add("hpo");
    }

    @Override
    protected String doTranslate(String input)
    {
        return input;
    }

    @Override
    public String getIdentifier()
    {
        return "microsoft";
    }

    @Override
    public Collection<String> getSupportedLanguages()
    {
        return SUPPORTED_LANGUAGES;
    }

    @Override
    public Collection<String> getSupportedVocabularies()
    {
        return SUPPORTED_VOCABULARIES;
    }
}
