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

package org.apache.stanbol.reasoners.web;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.commons.web.base.NavigationLink;

/**
 * The menue item for the Stanbol Reasoners component
 */
@Component
@Service(value=NavigationLink.class)
public class ReasonersMenueItem extends NavigationLink {

    private static final String NAME = "reasoners";

    private static final String htmlDescription = 
            "The entry point to multiple <strong>reasoning services</strong> that are used for"+
            "obtaining unexpressed additional knowledge from the explicit axioms in an ontology."+
            "Multiple reasoning profiles are available, each with its expressive power and computational cost.";

	public ReasonersMenueItem() {
		super(NAME, "/"+NAME, htmlDescription, 50);
	}
	
}
