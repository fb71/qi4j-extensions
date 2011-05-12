package org.qi4j.entitystore.lucene;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.qi4j.api.entity.EntityReference;
import org.qi4j.api.structure.Module;
import org.qi4j.spi.entity.EntityDescriptor;
import org.qi4j.spi.entity.EntityState;
import org.qi4j.spi.entitystore.EntityNotFoundException;
import org.qi4j.spi.entitystore.EntityStoreException;
import org.qi4j.spi.entitystore.EntityStoreSPI;
import org.qi4j.spi.entitystore.EntityStoreUnitOfWork;
import org.qi4j.spi.entitystore.StateCommitter;

/**
 * This {@link EntityStoreUnitOfWork} works with {@link LuceneEntityStoreMixin}.
 * <p>
 * This UnitOfWork holds the entity states in a HashMap mapping identity into
 * entity state in order to determine what entities have been accessed and/or
 * created already. I'm not sure if this is the intended way to do. I'll give
 * it a try...
 *
 * @author <a href="http://www.polymap.de">Falko Braeutigam</a>
 * @version POLYMAP3 ($Revision$)
 * @since 3.0
 */
public final class LuceneEntityStoreUnitOfWork
        implements EntityStoreUnitOfWork {

    private static Log log = LogFactory.getLog( LuceneEntityStoreUnitOfWork.class );

    private EntityStoreSPI          entityStoreSPI;

    private String                  identity;

    private Module                  module;

    private List<EntityState>       states = new ArrayList( 128 );


    public LuceneEntityStoreUnitOfWork( EntityStoreSPI entityStoreSPI, String identity,
            Module module ) {
        this.entityStoreSPI = entityStoreSPI;
        this.identity = identity;
        this.module = module;
    }


    public String identity() {
        return identity;
    }


    public Module module() {
        return module;
    }


    // EntityStore

    public EntityState newEntityState( EntityReference anIdentity, EntityDescriptor descriptor )
            throws EntityStoreException {
        EntityState entityState = entityStoreSPI.newEntityState( this, anIdentity, descriptor );
        states.add( entityState );
        return entityState;
    }


    public EntityState getEntityState( EntityReference reference )
            throws EntityStoreException, EntityNotFoundException {
        EntityState entityState = entityStoreSPI.getEntityState( this, reference );
        states.add( entityState );
        return entityState;
    }


    public StateCommitter apply()
            throws EntityStoreException {
        StateCommitter result = entityStoreSPI.apply( states, identity );
        return result;
    }


    public void discard() {
    }

}
