/*
 * Copyright 2012 JBoss Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.drools.workbench.client.perspectives;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.ui.PopupPanel;
import org.drools.workbench.client.resources.i18n.AppConstants;
import org.guvnor.m2repo.client.event.M2RepoRefreshEvent;
import org.guvnor.m2repo.client.event.M2RepoSearchEvent;
import org.guvnor.m2repo.client.upload.UploadForm;
import org.jboss.errai.ioc.client.container.SyncBeanManager;
import org.kie.workbench.common.widgets.client.search.ContextualSearch;
import org.kie.workbench.common.widgets.client.search.SearchBehavior;
import org.uberfire.client.annotations.Perspective;
import org.uberfire.client.annotations.WorkbenchMenu;
import org.uberfire.client.annotations.WorkbenchPerspective;
import org.uberfire.client.mvp.PlaceManager;
import org.uberfire.lifecycle.OnStartup;
import org.uberfire.mvp.Command;
import org.uberfire.mvp.impl.DefaultPlaceRequest;
import org.uberfire.security.annotations.Roles;
import org.uberfire.workbench.model.PanelType;
import org.uberfire.workbench.model.PerspectiveDefinition;
import org.uberfire.workbench.model.impl.PartDefinitionImpl;
import org.uberfire.workbench.model.impl.PerspectiveDefinitionImpl;
import org.uberfire.workbench.model.menu.MenuFactory;
import org.uberfire.workbench.model.menu.Menus;

/**
 * A Perspective to show M2_REPO related screen
 */
@Roles({ "admin" })
@ApplicationScoped
@WorkbenchPerspective(identifier = "org.guvnor.m2repo.client.perspectives.GuvnorM2RepoPerspective", isDefault = false)
public class M2RepoPerspective {

    @Inject
    private ContextualSearch contextualSearch;

    @Inject
    private Event<M2RepoSearchEvent> searchEvents;

    @Inject
    private Event<M2RepoRefreshEvent> refreshEvents;

    @Inject
    private PlaceManager placeManager;

    @Inject
    private SyncBeanManager iocManager;

    private Menus menus;

    @PostConstruct
    private void init() {
        buildMenuBar();
    }

    @WorkbenchMenu
    public Menus getMenus() {
        return this.menus;
    }

    @Perspective
    public PerspectiveDefinition getPerspective() {
        //UberFire's AbstractPanelManagerImpl performs destructive operations on a PerspectiveDefinition's Panels collection.
        //Therefore create a new instance of the perspective definition each time the definition is requested. Perspectives
        //that are not transient are not affected by the destructive operations as their definition is re-created when loaded.
        final PerspectiveDefinition perspective = new PerspectiveDefinitionImpl( PanelType.ROOT_STATIC );
        perspective.getRoot().addPart( new PartDefinitionImpl( new DefaultPlaceRequest( "M2RepoEditor" ) ) );
        perspective.setName( "M2 Repository Explorer" );
        perspective.setTransient( true );

        return perspective;
    }

    @OnStartup
    public void onStartup() {
        contextualSearch.setSearchBehavior( new SearchBehavior() {
            @Override
            public void execute( String searchFilter ) {
                searchEvents.fire( new M2RepoSearchEvent( searchFilter ) );
            }

        } );
    }

    private void buildMenuBar() {
        this.menus = MenuFactory.newTopLevelMenu( AppConstants.INSTANCE.Upload() )
                .respondsWith( new Command() {
                    @Override
                    public void execute() {
                        final UploadForm uploadForm = iocManager.lookupBean( UploadForm.class ).getInstance();
                        //When pop-up is closed destroy bean to avoid memory leak
                        uploadForm.addCloseHandler( new CloseHandler<PopupPanel>() {

                            @Override
                            public void onClose( CloseEvent<PopupPanel> event ) {
                                iocManager.destroyBean( uploadForm );
                            }

                        } );
                        uploadForm.show();
                    }
                } )
                .endMenu()
                .newTopLevelMenu( AppConstants.INSTANCE.Refresh() )
                .respondsWith( new Command() {
                    @Override
                    public void execute() {
                        refreshEvents.fire( new M2RepoRefreshEvent() );
                    }
                } )
                .endMenu()
                .build();
    }

}
