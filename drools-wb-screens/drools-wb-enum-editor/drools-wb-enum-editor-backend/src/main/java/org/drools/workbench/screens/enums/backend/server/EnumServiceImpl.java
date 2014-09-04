/*
 * Copyright 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.workbench.screens.enums.backend.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.inject.Named;

import org.drools.workbench.screens.enums.model.EnumModel;
import org.drools.workbench.screens.enums.model.EnumModelContent;
import org.drools.workbench.screens.enums.service.EnumService;
import org.drools.workbench.screens.enums.type.EnumResourceTypeDefinition;
import org.guvnor.common.services.backend.exceptions.ExceptionUtilities;
import org.guvnor.common.services.project.builder.events.InvalidateDMOPackageCacheEvent;
import org.guvnor.common.services.shared.file.CopyService;
import org.guvnor.common.services.shared.file.DeleteService;
import org.guvnor.common.services.shared.file.RenameService;
import org.guvnor.common.services.shared.metadata.MetadataService;
import org.guvnor.common.services.shared.metadata.model.Metadata;
import org.guvnor.common.services.shared.metadata.model.Overview;
import org.guvnor.common.services.shared.validation.model.ValidationMessage;
import org.jboss.errai.bus.server.annotations.Service;
import org.kie.api.builder.KieModule;
import org.kie.scanner.KieModuleMetaData;
import org.kie.workbench.common.services.backend.builder.LRUBuilderCache;
import org.kie.workbench.common.services.backend.service.KieService;
import org.kie.workbench.common.services.datamodel.backend.server.builder.util.DataEnumLoader;
import org.kie.workbench.common.services.shared.project.KieProject;
import org.kie.workbench.common.services.shared.project.KieProjectService;
import org.uberfire.backend.server.util.Paths;
import org.uberfire.backend.vfs.Path;
import org.uberfire.io.IOService;
import org.uberfire.java.nio.file.FileAlreadyExistsException;
import org.uberfire.workbench.events.ResourceOpenedEvent;

/**
 *
 */
@Service
@ApplicationScoped
public class EnumServiceImpl extends KieService implements EnumService {

    @Inject
    @Named("ioStrategy")
    private IOService ioService;

    @Inject
    private MetadataService metadataService;

    @Inject
    private CopyService copyService;

    @Inject
    private DeleteService deleteService;

    @Inject
    private RenameService renameService;

    @Inject
    private Event<InvalidateDMOPackageCacheEvent> invalidateDMOPackageCache;

    @Inject
    private Event<ResourceOpenedEvent> resourceOpenedEvent;

    @Inject
    private EnumResourceTypeDefinition resourceTypeDefinition;

    @Inject
    private KieProjectService projectService;

    @Inject
    private LRUBuilderCache builderCache;

    @Override
    public Path create( final Path context,
                        final String fileName,
                        final String content,
                        final String comment ) {
        try {
            final org.uberfire.java.nio.file.Path nioPath = Paths.convert( context ).resolve( fileName );
            final Path newPath = Paths.convert( nioPath );

            if ( ioService.exists( nioPath ) ) {
                throw new FileAlreadyExistsException( nioPath.toString() );
            }

            ioService.write( nioPath,
                             content,
                             makeCommentedOption( comment ) );

            return newPath;

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public String load( final Path path ) {
        try {
            final String content = ioService.readAllString( Paths.convert( path ) );

            return content;

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public EnumModelContent loadContent( final Path path ) {
        try {

            //Signal opening to interested parties
            resourceOpenedEvent.fire( new ResourceOpenedEvent( path,
                                                               sessionInfo ) );

            String text = load( path );
            return new EnumModelContent( new EnumModel( text ),
                                         loadOverview( path,
                                                       text ) );

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    private Overview loadOverview( final Path path,
                                   final String text ) {
        final Overview overview = super.loadOverview( path );
        overview.setPreview( text );
        return overview;
    }

    @Override
    public Path save( final Path resource,
                      final String content,
                      final Metadata metadata,
                      final String comment ) {
        try {
            ioService.write( Paths.convert( resource ),
                             content,
                             metadataService.setUpAttributes( resource,
                                                              metadata ),
                             makeCommentedOption( comment ) );

            //Invalidate Package-level DMO cache as Enums have changed.
            invalidateDMOPackageCache.fire( new InvalidateDMOPackageCacheEvent( resource ) );

            return resource;

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public void delete( final Path path,
                        final String comment ) {
        try {
            deleteService.delete( path,
                                  comment );

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public Path rename( final Path path,
                        final String newName,
                        final String comment ) {
        try {
            return renameService.rename( path,
                                         newName,
                                         comment );

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public Path copy( final Path path,
                      final String newName,
                      final String comment ) {
        try {
            return copyService.copy( path,
                                     newName,
                                     comment );

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public boolean accepts( final Path path ) {
        return resourceTypeDefinition.accept( path );
    }

    @Override
    public List<ValidationMessage> validate( final Path path ) {
        try {
            final String content = ioService.readAllString( Paths.convert( path ) );
            return validate( path,
                             content );

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public List<ValidationMessage> validate( final Path path,
                                             final String content ) {
        return doValidation( path,
                             content );
    }

    private List<ValidationMessage> doValidation( final Path path,
                                                  final String content ) {
        try {
            final KieProject project = projectService.resolveProject( path );
            final KieModule module = builderCache.assertBuilder( project ).getKieModuleIgnoringErrors();
            final ClassLoader classLoader = KieModuleMetaData.Factory.newKieModuleMetaData( module ).getClassLoader();
            final DataEnumLoader loader = new DataEnumLoader( content,
                                                              classLoader );
            if ( !loader.hasErrors() ) {
                return Collections.emptyList();
            } else {
                final List<ValidationMessage> validationMessages = new ArrayList<ValidationMessage>();
                final List<String> loaderErrors = loader.getErrors();

                for ( final String message : loaderErrors ) {
                    validationMessages.add( makeValidationMessages( path,
                                                                    message ) );
                }
                return validationMessages;
            }

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    private ValidationMessage makeValidationMessages( final Path path,
                                                      final String message ) {
        final ValidationMessage msg = new ValidationMessage();
        msg.setPath( path );
        msg.setLevel( ValidationMessage.Level.ERROR );
        msg.setText( message );
        return msg;
    }

}
