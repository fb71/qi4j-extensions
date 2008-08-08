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

import org.qi4j.entity.EntityComposite;
import org.qi4j.entity.association.Association;
import org.qi4j.property.Property;

/**
 * TODO
 */
public interface TestEntity
    extends EntityComposite
{
    Property<String> test1();

    Association<TestEntity> association();
}