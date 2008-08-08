/*
 * Copyright (c) 2008, Rickard Öberg. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.qi4j.rest.abdera;

import org.qi4j.bootstrap.Assembler;
import org.qi4j.bootstrap.AssemblyException;
import org.qi4j.bootstrap.ModuleAssembly;
import static org.qi4j.library.http.Dispatchers.Dispatcher.REQUEST;
import static org.qi4j.library.http.Servlets.addFilters;
import static org.qi4j.library.http.Servlets.addServlets;
import static org.qi4j.library.http.Servlets.filter;
import static org.qi4j.library.http.Servlets.serve;
import org.qi4j.library.http.UnitOfWorkFilterService;

/**
 * TODO
 */
public class AbderaServletAssembler implements Assembler
{
    public void assemble( ModuleAssembly module ) throws AssemblyException
    {
        addServlets( serve( "/*" ).with( Qi4jAbderaServletService.class ) ).to( module );
        addFilters( filter( "/*" ).through( UnitOfWorkFilterService.class ).on( REQUEST ) ).to( module );
    }
}