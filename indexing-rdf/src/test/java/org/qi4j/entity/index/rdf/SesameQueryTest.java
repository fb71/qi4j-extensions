/*
 * Copyright 2008 Alin Dreghiciu.
 *
 * Licensed  under the  Apache License,  Version 2.0  (the "License");
 * you may not use  this file  except in  compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed  under the  License is distributed on an "AS IS" BASIS,
 * WITHOUT  WARRANTIES OR CONDITIONS  OF ANY KIND, either  express  or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.qi4j.entity.index.rdf;

import org.junit.Test;
import org.qi4j.bootstrap.AssemblyException;
import org.qi4j.bootstrap.ModuleAssembly;
import org.qi4j.bootstrap.SingletonAssembler;
import org.qi4j.entity.UnitOfWorkCompletionException;
import org.qi4j.spi.entity.UuidIdentityGeneratorComposite;

public class SesameQueryTest
{
    @Test
    public void script01() throws UnitOfWorkCompletionException
    {
        SingletonAssembler assembler = new SingletonAssembler()
        {
            public void assemble( ModuleAssembly module ) throws AssemblyException
            {
                module.addComposites(
                    PersonComposite.class,
                    CityComposite.class,
                    DomainComposite.class
                );
                module.addServices(
                    IndexedMemoryEntityStoreComposite.class,
                    UuidIdentityGeneratorComposite.class,
                    RDFIndexerComposite.class
                );
            }
        };
        Network.populate( assembler.getUnitOfWorkFactory().newUnitOfWork() );
        SearchEngine searchEngine = assembler.getServiceLocator().lookupService( RDFIndexerComposite.class ).getService();
        searchEngine.findbyNativeQuery(
            "CONSTRUCT {Entity} id:identity {Identity} " +
            "FROM {Entity} id:identity {Identity}; rdf:type {qi4j:org.qi4j.entity.index.rdf.PersonComposite} " +
            "USING NAMESPACE " +
            "  id = <urn:org.qi4j.entity.Identity/>, " +
            "  qi4j = <urn:qi4j/>"
        );
    }

    @Test
    public void script02() throws UnitOfWorkCompletionException
    {
        SingletonAssembler assembler = new SingletonAssembler()
        {
            public void assemble( ModuleAssembly module ) throws AssemblyException
            {
                module.addComposites(
                    PersonComposite.class,
                    CityComposite.class,
                    DomainComposite.class,
                    GoogleComposite.class
                );
                module.addServices(
                    IndexedMemoryEntityStoreComposite.class,
                    UuidIdentityGeneratorComposite.class,
                    RDFIndexerComposite.class
                );
            }
        };
        Network.populate( assembler.getUnitOfWorkFactory().newUnitOfWork() );
        Google google = assembler.getCompositeBuilderFactory().newComposite( GoogleComposite.class );
        google.bornIn( "Kuala Lumpur" );
    }

}