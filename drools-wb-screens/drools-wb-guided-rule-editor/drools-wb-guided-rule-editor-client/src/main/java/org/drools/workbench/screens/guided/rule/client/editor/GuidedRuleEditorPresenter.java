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

package org.drools.workbench.screens.guided.rule.client.editor;

import java.util.List;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.New;
import javax.inject.Inject;

import com.google.gwt.user.client.ui.IsWidget;
import org.drools.workbench.models.datamodel.rule.RuleModel;
import org.drools.workbench.screens.guided.rule.client.editor.validator.GuidedRuleEditorValidator;
import org.drools.workbench.screens.guided.rule.client.resources.GuidedRuleEditorResources;
import org.drools.workbench.screens.guided.rule.client.type.GuidedRuleDRLResourceType;
import org.drools.workbench.screens.guided.rule.client.type.GuidedRuleDSLRResourceType;
import org.drools.workbench.screens.guided.rule.model.GuidedEditorContent;
import org.drools.workbench.screens.guided.rule.service.GuidedRuleEditorService;
import org.guvnor.common.services.project.service.ProjectService;
import org.guvnor.common.services.shared.metadata.model.Metadata;
import org.guvnor.common.services.shared.validation.model.ValidationMessage;
import org.guvnor.common.services.shared.version.events.RestoreEvent;
import org.jboss.errai.common.client.api.Caller;
import org.jboss.errai.common.client.api.RemoteCallback;
import org.kie.uberfire.client.callbacks.DefaultErrorCallback;
import org.kie.uberfire.client.callbacks.HasBusyIndicatorDefaultErrorCallback;
import org.kie.uberfire.client.common.MultiPageEditor;
import org.kie.uberfire.client.common.popups.errors.ErrorPopup;
import org.kie.workbench.common.services.datamodel.model.PackageDataModelOracleBaselinePayload;
import org.kie.workbench.common.services.shared.rulename.RuleNamesService;
import org.kie.workbench.common.widgets.client.callbacks.CommandBuilder;
import org.kie.workbench.common.widgets.client.callbacks.CommandDrivenErrorCallback;
import org.kie.workbench.common.widgets.client.datamodel.AsyncPackageDataModelOracle;
import org.kie.workbench.common.widgets.client.datamodel.AsyncPackageDataModelOracleFactory;
import org.kie.workbench.common.widgets.client.datamodel.ImportAddedEvent;
import org.kie.workbench.common.widgets.client.datamodel.ImportRemovedEvent;
import org.kie.workbench.common.widgets.client.discussion.VersionRecordManager;
import org.kie.workbench.common.widgets.client.menu.FileMenuBuilder;
import org.kie.workbench.common.widgets.client.popups.file.CommandWithCommitMessage;
import org.kie.workbench.common.widgets.client.popups.file.SaveOperationService;
import org.kie.workbench.common.widgets.client.popups.validation.DefaultFileNameValidator;
import org.kie.workbench.common.widgets.client.popups.validation.ValidationPopup;
import org.kie.workbench.common.widgets.client.resources.i18n.CommonConstants;
import org.kie.workbench.common.widgets.configresource.client.widget.bound.ImportsWidgetPresenter;
import org.kie.workbench.common.widgets.metadata.client.widget.OverviewWidgetPresenter;
import org.kie.workbench.common.widgets.viewsource.client.screen.ViewSourceView;
import org.uberfire.backend.vfs.ObservablePath;
import org.uberfire.backend.vfs.Path;
import org.uberfire.client.annotations.WorkbenchEditor;
import org.uberfire.client.annotations.WorkbenchMenu;
import org.uberfire.client.annotations.WorkbenchPartTitle;
import org.uberfire.client.annotations.WorkbenchPartView;
import org.uberfire.client.callbacks.Callback;
import org.uberfire.client.mvp.PlaceManager;
import org.uberfire.client.workbench.events.ChangeTitleWidgetEvent;
import org.uberfire.client.workbench.type.ClientResourceType;
import org.uberfire.client.workbench.type.ClientTypeRegistry;
import org.uberfire.java.nio.base.version.VersionRecord;
import org.uberfire.lifecycle.IsDirty;
import org.uberfire.lifecycle.OnClose;
import org.uberfire.lifecycle.OnMayClose;
import org.uberfire.lifecycle.OnSave;
import org.uberfire.lifecycle.OnStartup;
import org.uberfire.mvp.Command;
import org.uberfire.mvp.ParameterizedCommand;
import org.uberfire.mvp.PlaceRequest;
import org.uberfire.workbench.events.NotificationEvent;
import org.uberfire.workbench.model.menu.Menus;
import org.uberfire.workbench.type.FileNameUtil;

import static org.kie.uberfire.client.common.ConcurrentChangePopup.*;

@Dependent
@WorkbenchEditor(identifier = "GuidedRuleEditor", supportedTypes = {GuidedRuleDRLResourceType.class, GuidedRuleDSLRResourceType.class}, priority = 102)
public class GuidedRuleEditorPresenter {

    @Inject
    private ImportsWidgetPresenter importsWidget;

    @Inject
    private GuidedRuleEditorView view;

    @Inject
    private OverviewWidgetPresenter overview;

    @Inject
    private ViewSourceView viewSource;

    @Inject
    private MultiPageEditor multiPage;

    @Inject
    private Caller<GuidedRuleEditorService> service;

    @Inject
    private Caller<RuleNamesService> ruleNamesService;

    @Inject
    private Event<NotificationEvent> notification;

    @Inject
    private Event<ChangeTitleWidgetEvent> changeTitleNotification;

    @Inject
    private PlaceManager placeManager;

    @Inject
    private GuidedRuleDRLResourceType resourceTypeDRL;

    @Inject
    private GuidedRuleDSLRResourceType resourceTypeDSL;

    @Inject
    private AsyncPackageDataModelOracleFactory oracleFactory;

    @Inject
    private DefaultFileNameValidator fileNameValidator;

    @Inject
    private ClientTypeRegistry clientTypeRegistry;

    @Inject
    @New
    private FileMenuBuilder menuBuilder;

    @Inject
    @New
    private VersionRecordManager versionRecordManager;

    private Menus menus;

    @Inject
    private Caller<ProjectService> projectService;

    private ObservablePath path;
    private PlaceRequest place;
    private boolean isReadOnly;
    private boolean isDSLEnabled;
    private ObservablePath.OnConcurrentUpdateEvent concurrentUpdateSessionInfo = null;

    private RuleModel model;
    private AsyncPackageDataModelOracle oracle;
    private Metadata metadata;

    @OnStartup
    public void onStartup(final ObservablePath path,
            final PlaceRequest place) {
        this.place = place;
        init(path);

        versionRecordManager.addVersionSelectionCallback(
                new Callback<VersionRecord>() {
                    @Override
                    public void callback(VersionRecord versionRecord) {

                        view.showBusyIndicator(CommonConstants.INSTANCE.Loading());

                        if (versionRecordManager.isLatest(versionRecord)) {
                            isReadOnly = false;
                            versionRecordManager.setVersion(null);
                        } else {
                            isReadOnly = true;
                            versionRecordManager.setVersion(versionRecord.id());
                        }

                        GuidedRuleEditorPresenter.this.path = versionRecordManager.getCurrentPath();

                        loadContent();
                    }
                });

        addFileChangeListeners();

        makeMenuBar();

        loadContent();
    }

    private void init(ObservablePath path) {
        this.path = path;
        this.isReadOnly = place.getParameter("readOnly", null) == null ? false : true;
        this.isDSLEnabled = resourceTypeDSL.accept(path);

        versionRecordManager.setVersion(place.getParameter("version", null));
        versionRecordManager.setPathToLatest(path);
    }

    private void addFileChangeListeners() {
        this.path.onRename(new Command() {
            @Override
            public void execute() {
                //Effectively the same as reload() but don't reset concurrentUpdateSessionInfo
                changeTitleNotification.fire(new ChangeTitleWidgetEvent(place, getTitle(), null));
                loadContent();
            }
        });
        this.path.onDelete(new Command() {
            @Override
            public void execute() {
                placeManager.forceClosePlace(place);
            }
        });

        this.path.onConcurrentUpdate(new ParameterizedCommand<ObservablePath.OnConcurrentUpdateEvent>() {
            @Override
            public void execute(final ObservablePath.OnConcurrentUpdateEvent eventInfo) {
                concurrentUpdateSessionInfo = eventInfo;
            }
        });

        this.path.onConcurrentRename(new ParameterizedCommand<ObservablePath.OnConcurrentRenameEvent>() {
            @Override
            public void execute(final ObservablePath.OnConcurrentRenameEvent info) {
                newConcurrentRename(info.getSource(),
                        info.getTarget(),
                        info.getIdentity(),
                        new Command() {
                            @Override
                            public void execute() {
                                disableMenus();
                            }
                        },
                        new Command() {
                            @Override
                            public void execute() {
                                reload();
                            }
                        }
                ).show();
            }
        });

        this.path.onConcurrentDelete(new ParameterizedCommand<ObservablePath.OnConcurrentDelete>() {
            @Override
            public void execute(final ObservablePath.OnConcurrentDelete info) {
                newConcurrentDelete(info.getPath(),
                        info.getIdentity(),
                        new Command() {
                            @Override
                            public void execute() {
                                disableMenus();
                            }
                        },
                        new Command() {
                            @Override
                            public void execute() {
                                placeManager.closePlace(place);
                            }
                        }
                ).show();
            }
        });
    }

    private void reload() {
        concurrentUpdateSessionInfo = null;
        changeTitleNotification.fire(new ChangeTitleWidgetEvent(place, getTitle(), null));
        loadContent();
    }

    private void disableMenus() {
        menus.getItemsMap().get(FileMenuBuilder.MenuItems.COPY).setEnabled(false);
        menus.getItemsMap().get(FileMenuBuilder.MenuItems.RENAME).setEnabled(false);
        menus.getItemsMap().get(FileMenuBuilder.MenuItems.DELETE).setEnabled(false);
        menus.getItemsMap().get(FileMenuBuilder.MenuItems.VALIDATE).setEnabled(false);
    }

    private void loadContent() {
        view.showBusyIndicator(CommonConstants.INSTANCE.Loading());

        service.call(getModelSuccessCallback(),
                new CommandDrivenErrorCallback(view,
                        new CommandBuilder().addNoSuchFileException(view,
                                multiPage,
                                menus).build()
                )).loadContent(path);
    }

    private RemoteCallback<GuidedEditorContent> getModelSuccessCallback() {
        return new RemoteCallback<GuidedEditorContent>() {

            @Override
            public void callback(final GuidedEditorContent content) {
                //Path is set to null when the Editor is closed (which can happen before async calls complete).
                if (path == null) {
                    return;
                }


                multiPage.clear();
                multiPage.addWidget(overview,
                        CommonConstants.INSTANCE.Overview());

                multiPage.addWidget(view,
                        CommonConstants.INSTANCE.EditTabTitle());

                multiPage.addWidget(importsWidget,
                        CommonConstants.INSTANCE.ConfigTabTitle());

                GuidedRuleEditorPresenter.this.model = content.getModel();
                GuidedRuleEditorPresenter.this.metadata = content.getOverview().getMetadata();
                final PackageDataModelOracleBaselinePayload dataModel = content.getDataModel();
                oracle = oracleFactory.makeAsyncPackageDataModelOracle(path,
                        model,
                        dataModel);

                overview.setContent(content.getOverview(), clientTypeRegistry.resolve(path));

                versionRecordManager.setVersions(content.getOverview().getMetadata().getVersion());

                view.setContent(path,
                        model,
                        oracle,
                        ruleNamesService,
                        isReadOnly,
                        isDSLEnabled);
                importsWidget.setContent(oracle,
                        model.getImports(),
                        isReadOnly);

                view.hideBusyIndicator();
            }
        };
    }

    private void makeMenuBar() {
        menus = menuBuilder
                .addSave(new Command() {
                    @Override
                    public void execute() {
                        onSave();
                    }
                })
                .addCopy(path,
                        fileNameValidator)
                .addRename(path,
                        fileNameValidator)
                .addDelete(path)
                .addValidate(onValidate())
                .addNewTopLevelMenu(versionRecordManager.buildMenu())
                .build();
    }

    public void handleImportAddedEvent(@Observes ImportAddedEvent event) {
        if (!event.getDataModelOracle().equals(this.oracle)) {
            return;
        }
        view.refresh();
    }

    public void handleImportRemovedEvent(@Observes ImportRemovedEvent event) {
        if (!event.getDataModelOracle().equals(this.oracle)) {
            return;
        }
        view.refresh();
    }

    private Command onValidate() {
        return new Command() {
            @Override
            public void execute() {
                service.call(new RemoteCallback<List<ValidationMessage>>() {
                    @Override
                    public void callback(final List<ValidationMessage> results) {
                        if (results == null || results.isEmpty()) {
                            notification.fire(new NotificationEvent(CommonConstants.INSTANCE.ItemValidatedSuccessfully(),
                                    NotificationEvent.NotificationType.SUCCESS));
                        } else {
                            ValidationPopup.showMessages(results);
                        }
                    }
                }, new DefaultErrorCallback()).validate(path,
                        view.getContent());
            }
        };
    }

    @OnSave
    public void onSave() {

        if (isReadOnly && versionRecordManager.getVersion() == null) {
            view.alertReadOnly();
            return;
        } else if (isReadOnly && versionRecordManager.getVersion() != null) {
            versionRecordManager.restoreToCurrentVersion();
            return;
        }

        if (concurrentUpdateSessionInfo != null) {
            newConcurrentUpdate(concurrentUpdateSessionInfo.getPath(),
                    concurrentUpdateSessionInfo.getIdentity(),
                    new Command() {
                        @Override
                        public void execute() {
                            save();
                        }
                    },
                    new Command() {
                        @Override
                        public void execute() {
                            //cancel?
                        }
                    },
                    new Command() {
                        @Override
                        public void execute() {
                            reload();
                        }
                    }
            ).show();
        } else {
            save();
        }
    }

    private void save() {
        GuidedRuleEditorValidator validator = new GuidedRuleEditorValidator(model, GuidedRuleEditorResources.CONSTANTS);

        if (validator.isValid()) {
            new SaveOperationService().save(path,
                    new CommandWithCommitMessage() {
                        @Override
                        public void execute(final String commitMessage) {
                            view.showBusyIndicator(CommonConstants.INSTANCE.Saving());
                            service.call(getSaveSuccessCallback(),
                                    new HasBusyIndicatorDefaultErrorCallback(view)).save(path,
                                    view.getContent(),
                                    metadata,
                                    commitMessage);

                        }
                    }
            );

            concurrentUpdateSessionInfo = null;
        } else {
            ErrorPopup.showMessage(validator.getErrors().get(0));
        }
    }

    private RemoteCallback<Path> getSaveSuccessCallback() {
        return new RemoteCallback<Path>() {

            @Override
            public void callback(final Path path) {
                view.setNotDirty();
                view.hideBusyIndicator();
                versionRecordManager.reloadVersions(path);
                notification.fire(new NotificationEvent(CommonConstants.INSTANCE.ItemSavedSuccessfully()));
            }
        };
    }

    @IsDirty
    public boolean isDirty() {
        return view.isDirty();
    }

    @OnClose
    public void onClose() {
        this.path = null;
        this.oracleFactory.destroy(oracle);
    }

    @OnMayClose
    public boolean checkIfDirty() {
        if (isDirty()) {
            return view.confirmClose();
        }
        return true;
    }

    @WorkbenchPartTitle
    public String getTitle() {

        return view.getTitle(
                FileNameUtil.removeExtension(path, getResourceType()));
    }

    private ClientResourceType getResourceType() {
        if (resourceTypeDRL.accept(path)) {
            return resourceTypeDRL;
        } else {
            return resourceTypeDRL;
        }
    }

    @WorkbenchPartView
    public IsWidget getWidget() {

        return multiPage;
    }

    @WorkbenchMenu
    public Menus getMenus() {
        return menus;
    }

    public void onRestore(@Observes RestoreEvent restore) {
        if (path == null || restore == null || restore.getPath() == null) {
            return;
        }
        if (versionRecordManager.getPathToLatest().equals(restore.getPath())) {
            init(restore.getPath());
            loadContent();
            notification.fire(new NotificationEvent(CommonConstants.INSTANCE.ItemRestored()));
        }
    }

}
