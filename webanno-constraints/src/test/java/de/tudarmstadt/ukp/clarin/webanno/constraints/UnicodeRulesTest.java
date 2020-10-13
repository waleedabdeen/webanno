/*
 * Copyright 2020
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.constraints;

import static de.tudarmstadt.ukp.clarin.webanno.constraints.grammar.ConstraintsParser.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.junit.Test;

import de.tudarmstadt.ukp.clarin.webanno.constraints.evaluator.Evaluator;
import de.tudarmstadt.ukp.clarin.webanno.constraints.evaluator.PossibleValue;
import de.tudarmstadt.ukp.clarin.webanno.constraints.evaluator.ValuesGenerator;
import de.tudarmstadt.ukp.clarin.webanno.constraints.model.ParsedConstraints;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;

public class UnicodeRulesTest
{
    private static final String[] texts = {
            "This is a test.", // English
            "ይህ ፈተና ነው ፡፡", // Amharic
            "هذا اختبار.", // Arabic
            "Սա թեստ է.", // Armenian
            "এটা একটা পরীক্ষা.", // Bangla
            "Гэта тэст.", // Belarusian
            "Това е тест.", // Bulgarian
            "ဒါကစမ်းသပ်မှုတစ်ခု", // Burmese
            "Això és un test.", // Catalan
            "这是一个测试。", // Chinese (simplified)
            "這是一個測試。", // Chinese (traditional)
            "Ეს ტესტია.", // Georgian
            "Αυτό είναι ένα τεστ.", // Greek
            "આ એક કસોટી છે.", // Gujarati
            "זה מבחן.", // Hebrew
            "यह एक परीक्षण है।", // Hindi
            "Þetta er próf.", // Icelandic
            "🤷‍♀️" // Emoji
    };
    
    @Test
    public void thatRulesMatchUnicodeCharacters()
        throws Exception
    {
        JCas jcas = JCasFactory.createJCas();
        
        for (String text : texts) {
            jcas.reset();
            jcas.setDocumentText(text);
            
            ParsedConstraints constraints = parse(String.join("\n",
                    "import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma as Lemma;",
                    "Lemma {",
                    "  text() = \"" + text + "\" -> value=\"ok\";",
                    "}"));
            
            Lemma ann = new Lemma(jcas, 0, jcas.getDocumentText().length());
            ann.addToIndexes();
            
            Evaluator evaluator = new ValuesGenerator();
            List<PossibleValue> candidates = evaluator.generatePossibleValues(ann, "value",
                    constraints);
            
            assertThat(candidates).containsExactly(new PossibleValue("ok", false));
        }
    }

    @Test
    public void thatRulesCanAlsoNotMatch()
        throws Exception
    {
        JCas jcas = JCasFactory.createJCas();
        
        for (String text : texts) {
            jcas.reset();
            jcas.setDocumentText(text);
            
            ParsedConstraints constraints = parse(String.join("\n",
                    "import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma as Lemma;",
                    "Lemma {",
                    "  text() = \"" + StringUtils.reverse(text) + "\" -> value=\"ok\";",
                    "}"));
            
            Lemma ann = new Lemma(jcas, 0, jcas.getDocumentText().length());
            ann.addToIndexes();
            
            Evaluator evaluator = new ValuesGenerator();
            List<PossibleValue> candidates = evaluator.generatePossibleValues(ann, "value",
                    constraints);
            
            assertThat(candidates).isEmpty();
        }
    }
}
