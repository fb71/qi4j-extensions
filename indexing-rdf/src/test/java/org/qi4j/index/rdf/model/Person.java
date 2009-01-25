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
package org.qi4j.index.rdf.model;

import org.qi4j.api.entity.Queryable;
import org.qi4j.api.entity.association.Association;
import org.qi4j.api.entity.association.ManyAssociation;
import org.qi4j.api.property.Property;
import org.qi4j.api.common.Optional;

/**
 * TODO Add JavaDoc
 *
 * @author Alin Dreghiciu
 * @since March 20, 2008
 */
public interface Person
    extends Nameable, Alive
{
    @Optional Association<City> placeOfBirth();

    Property<Integer> yearOfBirth();

    @Optional Association<Female> mother();

    @Optional Association<Male> father();

    ManyAssociation<Domain> interests();

    @Optional Property<String> email();

    @Queryable( false ) Property<String> password();

    @Queryable( false ) @Optional Association<Account> mainAccount();

    @Queryable( false ) ManyAssociation<Account> accounts();
}