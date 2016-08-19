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

import org.xwiki.environment.Environment;
import org.xwiki.localization.LocalizationContext;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the common functionality in the abstract machine translator.
 * Works via a dummy concrete child.
 *
 * @version $Id$
 */
public class AbstractMachineTranslatorTest
{
    /**
     * The name of the vocabulary.
     */
    private static final String VOC_NAME = "dummy";

    /**
     * The mocker.
     */
    @Rule
    public final MockitoComponentMockingRule<AbstractMachineTranslator> mocker =
        new MockitoComponentMockingRule<AbstractMachineTranslator>(DummyMachineTranslator.class);

    /**
     * The temporary home of our translator.
     */
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /**
     * The component under test.
     */
    private AbstractMachineTranslator translator;

    /**
     * A vocabulary input term meant to already exist in the dummy translation file.
     * Check out dummy_dummy_es.xliff to see how it's specified.
     */
    private VocabularyInputTerm term1;

    /**
     * A vocabulary input term that is _not_ in the dummy translation file already.
     */
    private VocabularyInputTerm term2;

    /**
     * The translation fields we care about.
     */
    private Collection<String> fields = new HashSet<>();

    /**
     * Set up the test.
     */
    @Before
    public void setUp() throws Exception
    {
        Locale locale = new Locale("es");
        LocalizationContext ctx = mocker.getInstance(LocalizationContext.class);
        when(ctx.getCurrentLocale()).thenReturn(locale);

        Environment environment = mocker.getInstance(Environment.class);
        /* Gotta make sure it has a home so we can test that things are properly
         * persisted */
        when(environment.getPermanentDirectory()).thenReturn(folder.getRoot());

        term1 = mock(VocabularyInputTerm.class);
        when(term1.getId()).thenReturn("DUM:0001");
        when(term1.getName()).thenReturn("Dummy");
        when(term1.get("name")).thenReturn("Dummy");

        term2 = mock(VocabularyInputTerm.class);
        when(term2.getId()).thenReturn("DUM:0002");
        when(term2.getName()).thenReturn("Whatever");
        when(term2.get("name")).thenReturn("Whatever");

        fields.clear();
        fields.add("name");

        translator = spy(mocker.getComponentUnderTest());
        translator.loadVocabulary(VOC_NAME);
    }

    @After
    public void tearDown()
    {
        translator.unloadVocabulary(VOC_NAME);
    }

    /**
     * Test that the machine translator will not retranslate something that's already
     * in the file.
     */
    @Test
    public void testNoReTranslate()
    {
        long count = translator.translate(VOC_NAME, term1, fields);
        assertEquals(0, count);
        verify(translator, never()).doTranslate(term1.getName());
        verify(term1).set("name_es", "El Dummy");
    }

    /**
     * Test that the machine translator will translate when necessary.
     */
    @Test
    public void testDoTranslate()
    {
        long count = translator.translate(VOC_NAME, term2, fields);
        assertEquals(term2.getName().length(), count);
        verify(translator).doTranslate(term2.getName());
        verify(term2).set("name_es", "El Whatever");
    }

    /**
     * Test that previously performed translations will be cached.
     */
    @Test
    public void testRemember()
    {
        translator.translate(VOC_NAME, term2, fields);
        long count = translator.translate(VOC_NAME, term2, fields);
        assertEquals(0, count);
        verify(translator, times(1)).doTranslate(term2.getName());
        verify(term2, times(2)).set("name_es", "El Whatever");
    }

    /**
     * Test that we can't translate unless loadVocabulary has been invoked.
     */
    @Test
    public void testNotWhenUnloaded()
    {
        translator.unloadVocabulary(VOC_NAME);
        try {
            translator.translate(VOC_NAME, term1, fields);
            fail("Did not throw on translate when unloaded");
        } catch (IllegalStateException e) {
            /* So tearDown doesn't fail */
            translator.loadVocabulary(VOC_NAME);
        }
    }

    /**
     * Test that previously performed translations are remembered accross
     * restarts of the component.
     */
    @Test
    public void testPersist()
    {
        translator.translate(VOC_NAME, term2, fields);
        translator.unloadVocabulary(VOC_NAME);
        translator.loadVocabulary(VOC_NAME);
        long count = translator.translate(VOC_NAME, term2, fields);
        assertEquals(0, count);
        verify(translator, times(1)).doTranslate(term2.getName());
        verify(term2, times(2)).set("name_es", "El Whatever");
    }

    /**
     * Provides a dummy implementation of machine translator methods.
     *
     * @version $Id$
     */
    public static class DummyMachineTranslator extends AbstractMachineTranslator
    {
        @Override
        public Collection<String> getSupportedLanguages()
        {
            Collection<String> retval = new HashSet<>(1);
            retval.add("es");
            return retval;
        }

        @Override
        public Collection<String> getSupportedVocabularies()
        {
            Collection<String> retval = new HashSet<>(1);
            retval.add(VOC_NAME);
            return retval;
        }

        @Override
        public String getIdentifier()
        {
            return "dummy";
        }

        @Override
        protected String doTranslate(String msg)
        {
            return "El " + msg;
        }
    }
}
