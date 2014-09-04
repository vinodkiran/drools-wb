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

package org.drools.workbench.screens.guided.scorecard.backend.server;

import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.inject.Named;

import org.codehaus.plexus.util.StringUtils;
import org.drools.workbench.models.datamodel.oracle.PackageDataModelOracle;
import org.drools.workbench.models.guided.scorecard.backend.GuidedScoreCardXMLPersistence;
import org.drools.workbench.models.guided.scorecard.shared.Attribute;
import org.drools.workbench.models.guided.scorecard.shared.Characteristic;
import org.drools.workbench.models.guided.scorecard.shared.ScoreCardModel;
import org.drools.workbench.screens.guided.scorecard.model.ScoreCardModelContent;
import org.drools.workbench.screens.guided.scorecard.service.GuidedScoreCardEditorService;
import org.guvnor.common.services.backend.exceptions.ExceptionUtilities;
import org.guvnor.common.services.project.model.Package;
import org.guvnor.common.services.shared.file.CopyService;
import org.guvnor.common.services.shared.file.DeleteService;
import org.guvnor.common.services.shared.file.RenameService;
import org.guvnor.common.services.shared.metadata.model.Metadata;
import org.guvnor.common.services.shared.validation.model.ValidationMessage;
import org.jboss.errai.bus.server.annotations.Service;
import org.kie.workbench.common.services.backend.service.KieService;
import org.kie.workbench.common.services.datamodel.backend.server.DataModelOracleUtilities;
import org.kie.workbench.common.services.datamodel.backend.server.service.DataModelService;
import org.kie.workbench.common.services.datamodel.model.PackageDataModelOracleBaselinePayload;
import org.uberfire.backend.server.util.Paths;
import org.uberfire.backend.vfs.Path;
import org.uberfire.io.IOService;
import org.uberfire.java.nio.file.FileAlreadyExistsException;
import org.uberfire.workbench.events.ResourceOpenedEvent;

@Service
@ApplicationScoped
public class GuidedScoreCardEditorServiceImpl
        extends KieService
        implements GuidedScoreCardEditorService {

    @Inject
    @Named("ioStrategy")
    private IOService ioService;

    @Inject
    private CopyService copyService;

    @Inject
    private DeleteService deleteService;

    @Inject
    private RenameService renameService;

    @Inject
    private Event<ResourceOpenedEvent> resourceOpenedEvent;

    @Inject
    private DataModelService dataModelService;

    @Override
    public Path create( final Path context,
                        final String fileName,
                        final ScoreCardModel content,
                        final String comment ) {
        try {
            final Package pkg = projectService.resolvePackage( context );
            final String packageName = ( pkg == null ? null : pkg.getPackageName() );
            content.setPackageName( packageName );

            final org.uberfire.java.nio.file.Path nioPath = Paths.convert( context ).resolve( fileName );
            final Path newPath = Paths.convert( nioPath );

            if ( ioService.exists( nioPath ) ) {
                throw new FileAlreadyExistsException( nioPath.toString() );
            }

            ioService.write( nioPath,
                             GuidedScoreCardXMLPersistence.getInstance().marshal( content ),
                             makeCommentedOption( comment ) );

            return newPath;

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public ScoreCardModel load( final Path path ) {
        try {
            final String content = ioService.readAllString( Paths.convert( path ) );

            return GuidedScoreCardXMLPersistence.getInstance().unmarshall( content );

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public ScoreCardModelContent loadContent( final Path path ) {
        try {
            final ScoreCardModel model = load( path );
            final PackageDataModelOracle oracle = dataModelService.getDataModel( path );
            final PackageDataModelOracleBaselinePayload dataModel = new PackageDataModelOracleBaselinePayload();
            final GuidedScoreCardModelVisitor visitor = new GuidedScoreCardModelVisitor( model );
            DataModelOracleUtilities.populateDataModel( oracle,
                                                        dataModel,
                                                        visitor.getConsumedModelClasses() );

            //Signal opening to interested parties
            resourceOpenedEvent.fire( new ResourceOpenedEvent( path,
                                                               sessionInfo ) );

            return new ScoreCardModelContent( model,
                                              loadOverview( path ),
                                              dataModel );

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    @Override
    public Path save( final Path resource,
                      final ScoreCardModel model,
                      final Metadata metadata,
                      final String comment ) {
        try {
            final Package pkg = projectService.resolvePackage( resource );
            final String packageName = ( pkg == null ? null : pkg.getPackageName() );
            model.setPackageName( packageName );

            ioService.write( Paths.convert( resource ),
                             GuidedScoreCardXMLPersistence.getInstance().marshal( model ),
                             metadataService.setUpAttributes( resource, metadata ),
                             makeCommentedOption( comment ) );

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
    public String toSource( final Path path,
                            final ScoreCardModel model ) {
        try {
            final List<ValidationMessage> results = doValidation( model );
            if ( results.isEmpty() ) {
                return toDRL( path,
                              model );
            }
            return toValidationErrors( results );

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    private String toDRL( final Path path,
                          final ScoreCardModel model ) {
        return sourceServices.getServiceFor( Paths.convert( path ) ).getSource( Paths.convert( path ), model );
    }

    private String toValidationErrors( final List<ValidationMessage> results ) {
        final StringBuilder drl = new StringBuilder();
        for ( final ValidationMessage msg : results ) {
            drl.append( "//" ).append( msg.getText() ).append( "\n" );
        }
        return drl.toString();
    }

    @Override
    public List<ValidationMessage> validate( final Path path,
                                             final ScoreCardModel content ) {
        try {
            return doValidation( content );

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

    private List<ValidationMessage> doValidation( final ScoreCardModel model ) {
        final List<ValidationMessage> results = new ArrayList<ValidationMessage>();
        if ( StringUtils.isBlank( model.getFactName() ) ) {
            results.add( makeValidationMessages( "Fact Name is empty." ) );
        }
        if ( StringUtils.isBlank( model.getFieldName() ) ) {
            results.add( makeValidationMessages( "Resultant Score Field is empty." ) );
        }
        if ( model.getCharacteristics().size() == 0 ) {
            results.add( makeValidationMessages( "No Characteristics Found." ) );
        }
        int ctr = 1;
        for ( final Characteristic c : model.getCharacteristics() ) {
            String characteristicName = "Characteristic ('#" + ctr + "')";
            if ( StringUtils.isBlank( c.getName() ) ) {
                results.add( makeValidationMessages( "Characteristic Name '" + characteristicName + "' is empty." ) );
            } else {
                characteristicName = "Characteristic ('" + c.getName() + "')";
            }
            if ( StringUtils.isBlank( c.getFact() ) ) {
                results.add( makeValidationMessages( "Characteristic Name '" + characteristicName + "'. Fact is empty." ) );
            }
            if ( StringUtils.isBlank( c.getField() ) ) {
                results.add( makeValidationMessages( "Characteristic Name '" + characteristicName + "'. Characteristic Field is empty." ) );
            } else if ( StringUtils.isBlank( c.getDataType() ) ) {
                results.add( makeValidationMessages( "Characteristic Name '" + characteristicName + "'. Internal Error (missing datatype)." ) );
            }
            if ( c.getAttributes().size() == 0 ) {
                results.add( makeValidationMessages( "Characteristic Name '" + characteristicName + "'. No Attributes Found." ) );
            }
            if ( model.isUseReasonCodes() ) {
                if ( StringUtils.isBlank( model.getReasonCodeField() ) ) {
                    results.add( makeValidationMessages( "Characteristic Name '" + characteristicName + "'. Resultant Reason Codes Field is empty." ) );
                }
                if ( !"none".equalsIgnoreCase( model.getReasonCodesAlgorithm() ) ) {
                    results.add( makeValidationMessages( "Characteristic Name '" + characteristicName + "'. Baseline Score is not specified." ) );
                }
            }
            int attrCtr = 1;
            for ( final Attribute attribute : c.getAttributes() ) {
                final String attributeName = "Attribute ('#" + attrCtr + "')";
                if ( StringUtils.isBlank( attribute.getOperator() ) ) {
                    results.add( makeValidationMessages( "Attribute Name '" + attributeName + "'. Attribute Operator is empty." ) );
                }
                if ( StringUtils.isBlank( attribute.getValue() ) ) {
                    results.add( makeValidationMessages( "Attribute Name '" + attributeName + "'. Attribute Value is empty." ) );
                }
                if ( model.isUseReasonCodes() ) {
                    if ( StringUtils.isBlank( c.getReasonCode() ) ) {
                        if ( StringUtils.isBlank( attribute.getReasonCode() ) ) {
                            results.add( makeValidationMessages( "Attribute Name '" + attributeName + "'. Reason Code must be set at either attribute or characteristic." ) );
                        }
                    }
                }
                attrCtr++;
            }
            ctr++;
        }
        return results;
    }

    private ValidationMessage makeValidationMessages( final String message ) {
        final ValidationMessage msg = new ValidationMessage();
        msg.setText( message );
        return msg;
    }

}
