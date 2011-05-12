/* 
 * polymap.org
 * Copyright 2011, Falko Bräutigam, and individual contributors as indicated
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */
package org.qi4j.entitystore.lucene;

import org.apache.lucene.document.Document;

import org.qi4j.api.concern.Concerns;
import org.qi4j.api.mixin.Mixins;
import org.qi4j.api.service.Activatable;
import org.qi4j.api.service.ServiceComposite;
import org.qi4j.spi.entitystore.EntityStore;

/**
 * EntityStore backed by a Lucene index.
 * <p/>
 * Each entity is stored as a {@link Document} with different field mapping to
 * the fields of the entity.
 * <p/>
 * Property types are converted to native Preferences API types as much as
 * possible. All others will be serialized to a string using JSON.
 * <p/>
 * ManyAssociations are stored as multi-line strings (one identity per line),
 * and Associations are stored as the identity of the referenced Entity.
 * 
 * @see ServiceComposite
 * @see org.qi4j.api.configuration.Configuration
 */
 
@Mixins( 
        LuceneEntityStoreMixin.class 
)
public interface LuceneEntityStoreService
        extends LuceneSearcher, EntityStore, ServiceComposite/*, EntityStateVersions*/, Activatable {    
}
