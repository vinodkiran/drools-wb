/*
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.workbench.screens.testscenario.client;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;
import org.drools.workbench.models.datamodel.oracle.ModelField;
import org.drools.workbench.models.testscenarios.shared.ExecutionTrace;
import org.drools.workbench.models.testscenarios.shared.Scenario;
import org.drools.workbench.models.testscenarios.shared.VerifyFact;
import org.drools.workbench.models.testscenarios.shared.VerifyField;
import org.drools.workbench.screens.testscenario.client.resources.i18n.TestScenarioConstants;
import org.drools.workbench.screens.testscenario.client.resources.images.TestScenarioAltedImages;
import org.drools.workbench.screens.testscenario.client.resources.images.TestScenarioImages;
import org.kie.workbench.common.widgets.client.datamodel.AsyncPackageDataModelOracle;
import org.kie.workbench.common.widgets.client.resources.CommonAltedImages;
import org.kie.workbench.common.widgets.client.resources.CommonImages;
import org.uberfire.client.callbacks.Callback;
import org.kie.uberfire.client.common.FormStylePopup;
import org.kie.uberfire.client.common.SmallLabel;
import org.kie.uberfire.client.common.ValueChanged;

public class VerifyFactWidget extends Composite {

    private Grid outer;
    private boolean showResults;
    private String type;
    private AsyncPackageDataModelOracle oracle;
    private Scenario scenario;
    private ExecutionTrace executionTrace;

    public VerifyFactWidget( final VerifyFact vf,
                             final Scenario sc,
                             final AsyncPackageDataModelOracle oracle,
                             final ExecutionTrace executionTrace,
                             final boolean showResults ) {
        outer = new Grid( 2,
                          1 );
        outer.getCellFormatter().setStyleName( 0,
                                               0,
                                               "modeller-fact-TypeHeader" ); //NON-NLS
        outer.getCellFormatter().setAlignment( 0,
                                               0,
                                               HasHorizontalAlignment.ALIGN_CENTER,
                                               HasVerticalAlignment.ALIGN_MIDDLE );
        outer.setStyleName( "modeller-fact-pattern-Widget" ); //NON-NLS
        this.oracle = oracle;
        this.scenario = sc;
        this.executionTrace = executionTrace;
        HorizontalPanel ab = new HorizontalPanel();
        if ( !vf.anonymous ) {
            type = (String) sc.getVariableTypes().get( vf.getName() );
            ab.add( new SmallLabel( TestScenarioConstants.INSTANCE.scenarioFactTypeHasValues( type, vf.getName() ) ) );
        } else {
            type = vf.getName();
            ab.add( new SmallLabel( TestScenarioConstants.INSTANCE.AFactOfType0HasValues( vf.getName() ) ) );
        }
        this.showResults = showResults;

        Image add = TestScenarioAltedImages.INSTANCE.AddFieldToFact();
        add.setTitle( TestScenarioConstants.INSTANCE.AddAFieldToThisExpectation() );
        add.addClickHandler( new ClickHandler() {
            public void onClick( ClickEvent w ) {

                final ListBox b = new ListBox();
                VerifyFactWidget.this.oracle.getFieldCompletions( type,
                                                                  new Callback<ModelField[]>() {
                                                                      @Override
                                                                      public void callback( final ModelField[] fields ) {
                                                                          for ( int i = 0; i < fields.length; i++ ) {
                                                                              b.addItem( fields[ i ].getName() );
                                                                          }
                                                                      }
                                                                  } );

                final FormStylePopup pop = new FormStylePopup( TestScenarioAltedImages.INSTANCE.RuleAsset(),
                                                               TestScenarioConstants.INSTANCE.ChooseAFieldToAdd() );
                pop.addRow( b );
                Button ok = new Button( TestScenarioConstants.INSTANCE.OK() );
                ok.addClickHandler( new ClickHandler() {
                    public void onClick( ClickEvent w ) {
                        String f = b.getItemText( b.getSelectedIndex() );
                        vf.getFieldValues().add( new VerifyField( f,
                                                                  "",
                                                                  "==" ) );
                        FlexTable data = render( vf );
                        outer.setWidget( 1,
                                         0,
                                         data );
                        pop.hide();
                    }
                } );
                pop.addRow( ok );
                pop.show();

            }
        } );

        ab.add( add );
        outer.setWidget( 0,
                         0,
                         ab );
        initWidget( outer );

        FlexTable data = render( vf );
        outer.setWidget( 1,
                         0,
                         data );

    }

    private FlexTable render( final VerifyFact vf ) {
        FlexTable data = new FlexTable();
        for ( int i = 0; i < vf.getFieldValues().size(); i++ ) {
            final VerifyField fld = (VerifyField) vf.getFieldValues().get( i );
            data.setWidget( i,
                            1,
                            new SmallLabel( fld.getFieldName() + ":" ) );
            data.getFlexCellFormatter().setHorizontalAlignment( i,
                                                                1,
                                                                HasHorizontalAlignment.ALIGN_RIGHT );

            final ListBox opr = new ListBox();
            opr.addItem( TestScenarioConstants.INSTANCE.equalsScenario(),
                         "==" );
            opr.addItem( TestScenarioConstants.INSTANCE.doesNotEqualScenario(),
                         "!=" );
            if ( fld.getOperator().equals( "==" ) ) {
                opr.setSelectedIndex( 0 );
            } else {
                opr.setSelectedIndex( 1 );
            }
            opr.addChangeHandler( new ChangeHandler() {
                public void onChange( ChangeEvent event ) {
                    fld.setOperator( opr.getValue( opr.getSelectedIndex() ) );
                }
            } );

            data.setWidget( i,
                            2,
                            opr );
            Widget cellEditor = new VerifyFieldConstraintEditor( type,
                                                                 new ValueChanged() {
                                                                     public void valueChanged( String newValue ) {
                                                                         fld.setExpected( newValue );
                                                                     }

                                                                 },
                                                                 fld,
                                                                 oracle,
                                                                 this.scenario,
                                                                 this.executionTrace );

            data.setWidget( i,
                            3,
                            cellEditor );

            Image del = CommonAltedImages.INSTANCE.DeleteItemSmall();
            del.setAltText( TestScenarioConstants.INSTANCE.RemoveThisFieldExpectation() );
            del.setTitle( TestScenarioConstants.INSTANCE.RemoveThisFieldExpectation() );
            del.addClickHandler( new ClickHandler() {
                public void onClick( ClickEvent w ) {
                    if ( Window.confirm( TestScenarioConstants.INSTANCE.AreYouSureYouWantToRemoveThisFieldExpectation(
                            fld.getFieldName() ) ) ) {
                        vf.getFieldValues().remove( fld );
                        FlexTable data = render( vf );
                        outer.setWidget( 1,
                                         0,
                                         data );
                    }
                }
            } );
            data.setWidget( i,
                            4,
                            del );

            if ( showResults && fld.getSuccessResult() != null ) {
                if ( !fld.getSuccessResult().booleanValue() ) {
                    data.setWidget( i,
                                    0,
                                    new Image( CommonImages.INSTANCE.warning() ) );
                    data.setWidget( i,
                                    5,
                                    new HTML( TestScenarioConstants.INSTANCE.ActualResult( fld.getActualResult() ) ) );

                    data.getCellFormatter().addStyleName( i,
                                                          5,
                                                          "testErrorValue" ); //NON-NLS

                } else {
                    data.setWidget( i,
                                    0,
                                    new Image( TestScenarioImages.INSTANCE.testPassed() ) );
                }
            }

        }
        return data;
    }

}
