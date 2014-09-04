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

package org.drools.workbench.screens.dtablexls.client.editor;

import java.util.List;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import com.google.gwt.user.client.ui.IsWidget;
import org.drools.workbench.models.guided.dtable.shared.conversion.ConversionMessage;
import org.drools.workbench.models.guided.dtable.shared.conversion.ConversionResult;
import org.drools.workbench.screens.dtablexls.client.resources.i18n.DecisionTableXLSEditorConstants;
import org.drools.workbench.screens.dtablexls.client.type.DecisionTableXLSResourceType;
import org.drools.workbench.screens.dtablexls.client.widgets.ConversionMessageWidget;
import org.drools.workbench.screens.dtablexls.client.widgets.PopupListWidget;
import org.drools.workbench.screens.dtablexls.service.DecisionTableXLSContent;
import org.drools.workbench.screens.dtablexls.service.DecisionTableXLSService;
import org.guvnor.common.services.shared.validation.model.ValidationMessage;
import org.jboss.errai.common.client.api.Caller;
import org.jboss.errai.common.client.api.RemoteCallback;
import org.kie.uberfire.client.callbacks.DefaultErrorCallback;
import org.kie.uberfire.client.common.BusyIndicatorView;
import org.kie.workbench.common.widgets.client.popups.validation.ValidationPopup;
import org.kie.workbench.common.widgets.client.resources.i18n.CommonConstants;
import org.kie.workbench.common.widgets.metadata.client.KieEditor;
import org.uberfire.backend.vfs.ObservablePath;
import org.uberfire.client.annotations.WorkbenchEditor;
import org.uberfire.client.annotations.WorkbenchMenu;
import org.uberfire.client.annotations.WorkbenchPartTitle;
import org.uberfire.client.annotations.WorkbenchPartTitleDecoration;
import org.uberfire.client.annotations.WorkbenchPartView;
import org.uberfire.lifecycle.OnClose;
import org.uberfire.lifecycle.OnStartup;
import org.uberfire.mvp.Command;
import org.uberfire.mvp.PlaceRequest;
import org.uberfire.workbench.events.NotificationEvent;
import org.uberfire.workbench.model.menu.Menus;

@Dependent
@WorkbenchEditor(identifier = "DecisionTableXLSEditor", supportedTypes = { DecisionTableXLSResourceType.class })
public class DecisionTableXLSEditorPresenter
        extends KieEditor
        implements DecisionTableXLSEditorView.Presenter {

    @Inject
    private Caller<DecisionTableXLSService> decisionTableXLSService;

    @Inject
    private Event<NotificationEvent> notification;

    @Inject
    private BusyIndicatorView busyIndicatorView;

    @Inject
    private DecisionTableXLSResourceType type;

    private DecisionTableXLSEditorView view;

    @Inject
    public DecisionTableXLSEditorPresenter( final DecisionTableXLSEditorView baseView ) {
        super( baseView );
        view = baseView;
    }

    @OnStartup
    public void onStartup( final ObservablePath path,
                           final PlaceRequest place ) {
        super.init( path,
                    place,
                    type );
        view.init( this );
    }

    @Override
    protected void loadContent() {
        decisionTableXLSService.call( new RemoteCallback<DecisionTableXLSContent>() {
            @Override
            public void callback( final DecisionTableXLSContent content ) {
                resetEditorPages( content.getOverview() );
                addSourcePage();

                view.setPath( versionRecordManager.getCurrentPath() );
                view.setReadOnly( isReadOnly );
            }
        } ).loadContent( versionRecordManager.getCurrentPath() );
    }

    @Override
    protected void onSourceTabSelected() {
        decisionTableXLSService.call( new RemoteCallback<String>() {
            @Override
            public void callback( String source ) {
                updateSource( source );
            }
        } ).getSource( versionRecordManager.getCurrentPath() );
    }

    @Override
    protected Command onValidate() {
        return new Command() {
            @Override
            public void execute() {
                decisionTableXLSService.call( new RemoteCallback<List<ValidationMessage>>() {
                    @Override
                    public void callback( final List<ValidationMessage> results ) {
                        if ( results == null || results.isEmpty() ) {
                            notification.fire( new NotificationEvent( CommonConstants.INSTANCE.ItemValidatedSuccessfully(),
                                                                      NotificationEvent.NotificationType.SUCCESS ) );
                        } else {
                            ValidationPopup.showMessages( results );
                        }
                    }
                }, new DefaultErrorCallback() ).validate( versionRecordManager.getCurrentPath(),
                                                          versionRecordManager.getCurrentPath() );
            }
        };
    }

    @Override
    protected void makeMenuBar() {
        menus = menuBuilder
                .addCopy( versionRecordManager.getCurrentPath(),
                          fileNameValidator )
                .addRename( versionRecordManager.getPathToLatest(),
                            fileNameValidator )
                .addDelete( versionRecordManager.getPathToLatest() )
                .addValidate( onValidate() )
                .addCommand( DecisionTableXLSEditorConstants.INSTANCE.Convert(),
                             new Command() {

                                 @Override
                                 public void execute() {
                                     convert();
                                 }
                             }
                           )
                .addNewTopLevelMenu( versionRecordManager.buildMenu() )
                .build();
    }

    @OnClose
    public void onClose() {
        this.versionRecordManager.clear();
    }

    @WorkbenchPartTitleDecoration
    public IsWidget getTitle() {
        return super.getTitle();
    }

    @WorkbenchPartTitle
    public String getTitleText() {
        return super.getTitleText();
    }

    @WorkbenchPartView
    public IsWidget getWidget() {
        return super.getWidget();
    }

    @WorkbenchMenu
    public Menus getMenus() {
        return menus;
    }

    private void convert() {
        busyIndicatorView.showBusyIndicator( DecisionTableXLSEditorConstants.INSTANCE.Converting() );
        decisionTableXLSService.call( new RemoteCallback<ConversionResult>() {
            @Override
            public void callback( final ConversionResult response ) {
                busyIndicatorView.hideBusyIndicator();
                if ( response.getMessages().size() > 0 ) {
                    final PopupListWidget popup = new PopupListWidget();
                    for ( ConversionMessage message : response.getMessages() ) {
                        popup.addListItem( new ConversionMessageWidget( message ) );
                    }
                    popup.show();
                }
            }
        } ).convert( versionRecordManager.getCurrentPath() );
    }

}
