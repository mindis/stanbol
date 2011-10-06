/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.stanbol.commons.jsonld;

import static org.junit.Assert.*;

import org.junit.Test;

public class JsonLdParserTest {

    @Test
    public void testParseWithProfile() throws Exception {
        String jsonldInput = "{\"@context\":{\"iks\":\"http://iks-project.eu/ont/\",\"upb\":\"http://upb.de/persons/\"},\"@profile\":\"iks:employeeOf\",\"person\":{\"@iri\":\"upb:bnagel\"},\"organization\":{\"@iri\":\"http://uni-paderborn.de\"}}";
        
        JsonLd jsonLd = JsonLdParser.parse(jsonldInput);
        jsonLd.setUseTypeCoercion(true);
        jsonLd.setUseCuries(true);
        
        String actual = jsonLd.toString();
        String expected = "{\"@context\":{\"iks\":\"http://iks-project.eu/ont/\",\"upb\":\"http://upb.de/persons/\"},\"@subject\":\"_:bnode1\",\"@profile\":\"iks:employeeOf\",\"organization\":{\"@iri\":\"http://uni-paderborn.de\"},\"person\":{\"@iri\":\"upb:bnagel\"}}";
        assertEquals(expected, actual);
        assertNotNull(jsonLd);
    }
    
    @Test
    public void testParse3() throws Exception {
        String jsonldInput = "{\"@context\":{\"iks\":\"http://iks-project.eu/ont/\",\"upb\":\"http://upb.de/persons/\"},\"@profile\":\"iks:employeeOf\",\"@subject\":[{\"person\":{\"@iri\":\"upb:bnagel\"},\"organization\":{\"@iri\":\"http://uni-paderborn.de\"}},{\"person\":{\"@iri\":\"upb:fchrist\"},\"organization\":{\"@iri\":\"http://uni-paderborn.de\"}}]}";
        
        JsonLd jsonLd = JsonLdParser.parse(jsonldInput);
        jsonLd.setUseTypeCoercion(true);
        jsonLd.setApplyNamespaces(false);
        
        String actual = jsonLd.toString();
        String expected = "{\"@subject\":[{\"@subject\":\"_:bnode1\",\"@profile\":\"http://iks-project.eu/ont/employeeOf\",\"organization\":{\"@iri\":\"http://uni-paderborn.de\"},\"person\":{\"@iri\":\"http://upb.de/persons/bnagel\"}},{\"@subject\":\"_:bnode2\",\"@profile\":\"http://iks-project.eu/ont/employeeOf\",\"organization\":{\"@iri\":\"http://uni-paderborn.de\"},\"person\":{\"@iri\":\"http://upb.de/persons/fchrist\"}}]}";
        assertEquals(expected, actual);
        assertNotNull(jsonLd);
    }
    
    @Test
    public void testParse4() throws Exception {
        String jsonldInput = "{\"@context\":{\"iks\":\"http://iks-project.eu/ont/\",\"upb\":\"http://upb.de/persons/\"},\"@profile\":\"iks:employeeOf\",\"@subject\":[{\"person\":{\"@iri\":\"upb:bnagel\"},\"organization\":{\"@iri\":\"http://uni-paderborn.de\"}},{\"person\":{\"@iri\":\"upb:fchrist\"},\"organization\":{\"@iri\":\"http://uni-paderborn.de\"}}]}";
        
        JsonLd jsonLd = JsonLdParser.parse(jsonldInput);
        jsonLd.setUseTypeCoercion(true);
        jsonLd.setApplyNamespaces(true);
        
        String actual = jsonLd.toString();
        String expected = "{\"@context\":{\"bnagel\":\"http://upb.de/persons/bnagel\",\"employeeOf\":\"http://iks-project.eu/ont/employeeOf\",\"fchrist\":\"http://upb.de/persons/fchrist\"},\"@subject\":[{\"@subject\":\"_:bnode1\",\"@profile\":\"employeeOf\",\"organization\":{\"@iri\":\"http://uni-paderborn.de\"},\"person\":{\"@iri\":\"bnagel\"}},{\"@subject\":\"_:bnode2\",\"@profile\":\"employeeOf\",\"organization\":{\"@iri\":\"http://uni-paderborn.de\"},\"person\":{\"@iri\":\"fchrist\"}}]}";
        assertEquals(expected, actual);
        assertNotNull(jsonLd);
    }
    
    @Test
    public void testParse5() throws Exception {
        String jsonldInput = "{\"@context\":{\"iks\":\"http://iks-project.eu/ont/\",\"upb\":\"http://upb.de/persons/\"},\"@profile\":\"iks:employeeOf\",\"@subject\":[{\"person\":{\"@iri\":\"upb:bnagel\"},\"organization\":{\"@iri\":\"http://uni-paderborn.de\"}},{\"person\":{\"@iri\":\"upb:fchrist\"},\"organization\":{\"@iri\":\"http://uni-paderborn.de\"}}]}";
        
        JsonLd jsonLd = JsonLdParser.parse(jsonldInput);
        jsonLd.setUseTypeCoercion(true);
        jsonLd.setApplyNamespaces(true);
        jsonLd.setUseJointGraphs(false);
        jsonLd.setUseCuries(true);
        
        String actual = jsonLd.toString();
        String expected = "[{\"@context\":{\"iks\":\"http://iks-project.eu/ont/\",\"upb\":\"http://upb.de/persons/\"},\"@subject\":\"_:bnode1\",\"@profile\":\"iks:employeeOf\",\"organization\":{\"@iri\":\"http://uni-paderborn.de\"},\"person\":{\"@iri\":\"upb:bnagel\"}},{\"@context\":{\"iks\":\"http://iks-project.eu/ont/\",\"upb\":\"http://upb.de/persons/\"},\"@subject\":\"_:bnode2\",\"@profile\":\"iks:employeeOf\",\"organization\":{\"@iri\":\"http://uni-paderborn.de\"},\"person\":{\"@iri\":\"upb:fchrist\"}}]";
        assertEquals(expected, actual);
        assertNotNull(jsonLd);
    }
    
    @Test
    public void testParse6() throws Exception {
        String jsonldInput = "{\"@context\":{\"iks\":\"http://iks-project.eu/ont/\",\"upb\":\"http://upb.de/persons/\"},\"@subject\":[{\"@profile\":\"iks:employeeOf\",\"person\":{\"@iri\":\"upb:bnagel\"},\"organization\":{\"@iri\":\"http://uni-paderborn.de\"}},{\"@profile\":\"iks:friendOf\",\"person\":{\"@iri\":\"upb:bnagel\"},\"friend\":{\"@iri\":\"upb:fchrist\"}}]}";
        
        JsonLd jsonLd = JsonLdParser.parse(jsonldInput);
        jsonLd.setUseTypeCoercion(true);
        jsonLd.setApplyNamespaces(true);
        jsonLd.setUseJointGraphs(true);
        
        String actual = jsonLd.toString();
        String expected = "{\"@context\":{\"bnagel\":\"http://upb.de/persons/bnagel\",\"employeeOf\":\"http://iks-project.eu/ont/employeeOf\",\"fchrist\":\"http://upb.de/persons/fchrist\",\"friendOf\":\"http://iks-project.eu/ont/friendOf\"},\"@subject\":[{\"@subject\":\"_:bnode1\",\"@profile\":\"employeeOf\",\"organization\":{\"@iri\":\"http://uni-paderborn.de\"},\"person\":{\"@iri\":\"bnagel\"}},{\"@subject\":\"_:bnode2\",\"@profile\":\"friendOf\",\"friend\":{\"@iri\":\"fchrist\"},\"person\":{\"@iri\":\"bnagel\"}}]}";
        assertEquals(expected, actual);
        assertNotNull(jsonLd);
    }
    
    @Test
    public void testParse7() throws Exception {
        String jsonldInput = "{\"@context\":{\"iks\":\"http://iks-project.eu/ont/\",\"upb\":\"http://upb.de/persons/\"},\"@profile\":\"iks:employeeOf\",\"person\":\"Benjamin\",\"organization\":\"UniPaderborn\"}";
        
        JsonLd jsonLd = JsonLdParser.parse(jsonldInput);
        jsonLd.setUseTypeCoercion(true);
        jsonLd.setApplyNamespaces(true);
        jsonLd.setUseJointGraphs(true);
        jsonLd.setUseCuries(true);
        
        String actual = jsonLd.toString();
        String expected = "{\"@context\":{\"iks\":\"http://iks-project.eu/ont/\"},\"@subject\":\"_:bnode1\",\"@profile\":\"iks:employeeOf\",\"organization\":\"UniPaderborn\",\"person\":\"Benjamin\"}";
        assertEquals(expected, actual);
        assertNotNull(jsonLd);
    }
    
    @SuppressWarnings("unused")
    private void toConsole(String actual) {
        System.out.println(actual);
        String s = actual;
        s = s.replaceAll("\\\\", "\\\\\\\\");
        s = s.replace("\"", "\\\"");
        s = s.replace("\n", "\\n");
        System.out.println(s);
    }
}
