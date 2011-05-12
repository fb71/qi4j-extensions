/*
 * Copyright (c) 2008, Rickard Öberg. All Rights Reserved.
 * Copyright (c) 2008, Falko Bräutigam. All Rights Reserved.
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
package org.qi4j.entitystore.lucene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import java.lang.ref.WeakReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.qi4j.api.Qi4j;
import org.qi4j.api.common.QualifiedName;
import org.qi4j.api.concern.ConcernOf;
import org.qi4j.api.entity.EntityReference;
import org.qi4j.api.injection.scope.Structure;
import org.qi4j.api.structure.Module;
import org.qi4j.api.usecase.Usecase;
import org.qi4j.spi.entity.EntityDescriptor;
import org.qi4j.spi.entity.EntityState;
import org.qi4j.spi.entitystore.ConcurrentEntityStateModificationException;
import org.qi4j.spi.entitystore.EntityNotFoundException;
import org.qi4j.spi.entitystore.EntityStore;
import org.qi4j.spi.entitystore.EntityStoreException;
import org.qi4j.spi.entitystore.EntityStoreUnitOfWork;
import org.qi4j.spi.entitystore.StateCommitter;

import org.polymap.core.model.Entity;

/**
 * Concern that helps EntityStores do concurrent modification checks.
 * <p/>
 * It caches the versions of state that it loads, and forgets them when the
 * state is committed. For normal operation this means that it does not have to
 * go down to the underlying store to get the current version. Whenever there is
 * a concurrent modification the store will most likely have to check with the
 * underlying store what the current version is.
 * <p/>
 * This implementation does never go to the underlying store. It holds all
 * versions of every entity that was saved for the livetime of this store
 * (probably JVM run).
 * 
 * @deprecated
 */
public abstract class ConcurrentModificationCheckConcern
        extends ConcernOf<EntityStore>
        implements EntityStore {

    private static Log log = LogFactory.getLog( ConcurrentModificationCheckConcern.class );

    private static final QualifiedName      qn = QualifiedName.fromClass( Entity.class, "_lastModified" );

    @Structure
    private Qi4j                            api;

    // FIXME !!! revert this back to non-static as soon as this is integrated into
    // polymap3 mode/entity system
    private static Map<EntityReference,Long> versions = new HashMap<EntityReference, Long>();

    
    public EntityStoreUnitOfWork newUnitOfWork( Usecase usecase, Module module ) {
        final EntityStoreUnitOfWork uow = next.newUnitOfWork( usecase, module );
        return new ConcurrentCheckingEntityStoreUnitOfWork( uow, module );
    }

    
    public static boolean hasChanged( Entity entity ) {
        assert entity != null;

        log.warn( "No check currently!" );
        return false;
        
//        Long entityLastModified = entityLastModified( entity.state() );  //entity._lastModified().get();
//        Long storeLastModified = versions.get( EntityReference.parseEntityReference( entity.id() ) );
//        
//        log.debug( "hasChanged(): entity: " + entity.id() );
//        log.debug( "    entity=" + entityLastModified );
//        log.debug( "    store= " + storeLastModified );
//        return storeLastModified != null && !entityLastModified.equals( storeLastModified );
    }

    
    /**
     * 
     */
    private class ConcurrentCheckingEntityStoreUnitOfWork
            implements EntityStoreUnitOfWork {

        private final EntityStoreUnitOfWork     delegate;

        private Module                          module;

        /** 
         * If nobody else holds a hard reference to a particular entityState,
         * then it is probably not changed and it is ok to loose the reference
         * and so not checking concurrent modification on next apply();
         */
        private List<WeakReference<EntityState>> loaded = new ArrayList();


        public ConcurrentCheckingEntityStoreUnitOfWork( EntityStoreUnitOfWork uow, Module module ) {
            this.delegate = uow;
            this.module = module;
        }


        public String identity() {
            return delegate.identity();
        }


        public EntityState newEntityState( EntityReference anIdentity,
                EntityDescriptor entityDescriptor )
                throws EntityStoreException {
            return delegate.newEntityState( anIdentity, entityDescriptor );
        }


        public StateCommitter apply()
                throws EntityStoreException {
            
            // check concurrent modification
            List<EntityReference> changed = new ArrayList();
            final List<EntityState> updated = new ArrayList();
            final long now = System.currentTimeMillis();
            
            for (Iterator<WeakReference<EntityState>> it=loaded.iterator(); it.hasNext(); ) {
                EntityState entityState = it.next().get();

                if (entityState == null) {
                    log.debug( "apply(): entityState reclaimed by GC !!!" ); 
                    it.remove();
                    continue;
                }
                
                switch (entityState.status()) {
                    case LOADED: {
                        continue;
                    }
                    case NEW:
                    case UPDATED: {
                        Long storeLastModified = versions.get( entityState.identity() );
                        Long entityLastModified = entityLastModified( entityState );

                        // check concurrent change
                        log.debug( "CHECK: entityLastModified=" + entityLastModified + ", storeLastModified=" + storeLastModified );
                        if (storeLastModified != null && !storeLastModified.equals( entityLastModified )) {
                            log.debug( "CHANGED: " + entityState.identity() 
                                    + ", lastModified=" + entityLastModified + ", storeLastModified=" + storeLastModified );
                            changed.add( entityState.identity() );
                        }
                        // set timestamp
                        log.debug( "APPLY: setting " + qn.name() + " of entity: " + entityState.identity() 
                                + "\n    _lastModified=" + entityLastModified 
                                + "\n    now          =" + now );
                        entityState.setProperty( qn, now );
                        
                        updated.add( entityState );
                        break;
                    }                        
                    case REMOVED: {
                        it.remove();
                        break;
                    }                        
                }
            }
            if (!changed.isEmpty()) {
                throw new ConcurrentEntityStateModificationException( changed );
            }            

            //
            final StateCommitter committer = delegate.apply();

            //
            return new StateCommitter() {

                public void commit() {
//                    // update _lastModified
//                    long now = System.currentTimeMillis();
//                    for (EntityState entityState : updated) {
//                        Object lastModified = entityState.getProperty( qn );
//                        log.debug( "COMMIT: setting " + qn.name() + " of entity: " + entityState.identity() 
//                                + "\n    _lastModified=" + lastModified 
//                                + "\n    now          =" + now );
//                        entityState.setProperty( qn, now );
//                    }

                    committer.commit();
                    
                    // updateVersions
                    // XXX check race cond between different uow commits?
                    for (EntityState entityState : updated) {
                        Long lastModified = entityLastModified( entityState );
                        log.debug( "COMMIT: updating global timestamp of entity: " + entityState.identity() 
                                + "\n    lastModified=" + lastModified );
                        versions.put( entityState.identity(), lastModified );
                    }
                }

                public void cancel() {
                    committer.cancel();
                }
            };
        }


        public void discard() {
            try {
                delegate.discard();
            }
            finally {
                // XXX remove my loaded entity from the global versions table if
                // no other uow holds a reference to this entity
            }
        }


        public EntityState getEntityState( EntityReference anIdentity )
                throws EntityStoreException, EntityNotFoundException {
            EntityState entityState = delegate.getEntityState( anIdentity );
            
            //versions.rememberVersion( entityState.identity(), entityState.version() );
            Long storeLastModified = versions.get( entityState.identity() );
            Long entityLastModified = entityLastModified( entityState );
            if (storeLastModified != null && entityLastModified != null && !storeLastModified.equals( entityLastModified )) {
                throw new EntityStoreException( "Loaded entity has different version as globally recorded version." );
            }
            
            loaded.add( new WeakReference( entityState ) );
            return entityState;
        }

        
        protected Long entityLastModified( EntityState entityState ) {
            return (Long)entityState.getProperty( qn );
//            return entityState.lastModified();
        }

    }

}
