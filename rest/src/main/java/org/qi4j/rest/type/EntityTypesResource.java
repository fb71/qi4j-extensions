/*
 * Copyright (c) 2008, Niclas Hedhman. All Rights Reserved.
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
package org.qi4j.rest.type;

import org.qi4j.injection.scope.Uses;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.ResourceException;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;

public class EntityTypesResource extends Resource
{
    public EntityTypesResource( @Uses Context context, @Uses Request request, @Uses Response response )
    {
        super( context, request, response );

        getVariants().add( new Variant( MediaType.TEXT_PLAIN ) );
    }

    @Override
    public Representation represent( Variant variant ) throws ResourceException
    {
        Representation representation = new StringRepresentation(
            "hello, world", MediaType.TEXT_PLAIN );
        return representation;
    }

}