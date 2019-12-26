/*
 *
 *  * ******************************************************************************
 *  * Copyright (C) 2014-2019 Dennis Sheirer
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *  * *****************************************************************************
 *
 *
 */

package io.github.dsheirer.gui.playlist.channel;

import io.github.dsheirer.alias.AliasEvent;
import io.github.dsheirer.alias.AliasModel;
import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.gui.playlist.Editor;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.decode.config.DecodeConfiguration;
import io.github.dsheirer.module.log.config.EventLogConfiguration;
import io.github.dsheirer.record.config.RecordConfiguration;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.source.config.SourceConfiguration;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Channel configuration editor
 */
public abstract class ChannelConfigurationEditor extends Editor<Channel>
{
    private final static Logger mLog = LoggerFactory.getLogger(ChannelConfigurationEditor.class);

    private AliasModel mAliasModel;
    protected EditorModificationListener mEditorModificationListener = new EditorModificationListener();
    private AliasModelChangeListener mAliasModelChangeListener = new AliasModelChangeListener();
    private TextField mSystemField;
    private TextField mSiteField;
    private TextField mNameField;
    private ComboBox<String> mAliasListComboBox;
    private GridPane mTextFieldPane;
    private Button mSaveButton;
    private Button mResetButton;
    private VBox mButtonBox;

    public ChannelConfigurationEditor(AliasModel aliasModel)
    {
        mAliasModel = aliasModel;

        //Listen for alias change events so we can update the alias list combo box
        mAliasModel.addListener(mAliasModelChangeListener);

        HBox textFieldsAndButtonsBox = new HBox();
        HBox.setHgrow(textFieldsAndButtonsBox, Priority.ALWAYS);
        HBox.setHgrow(getButtonBox(), Priority.NEVER);
        textFieldsAndButtonsBox.getChildren().addAll(getTextFieldPane(), getButtonBox());
        getChildren().add(textFieldsAndButtonsBox);
    }

    @Override
    public void dispose()
    {
        mAliasModel.removeListener(mAliasModelChangeListener);
    }

    public abstract DecoderType getDecoderType();

    @Override
    public void setItem(Channel channel)
    {
        super.setItem(channel);

        getSystemField().setText(channel.getSystem());
        getSiteField().setText(channel.getSite());
        getNameField().setText(channel.getName());

        String aliasListName = channel.getAliasListName();

        if(aliasListName != null)
        {
            if(!getAliasListComboBox().getItems().contains(aliasListName))
            {
                getAliasListComboBox().getItems().add(aliasListName);
            }
            getAliasListComboBox().getSelectionModel().select(aliasListName);
        }

        setDecoderConfiguration(channel.getDecodeConfiguration());
        setEventLogConfiguration(channel.getEventLogConfiguration());
        setRecordConfiguration(channel.getRecordConfiguration());
        setSourceConfiguration(channel.getSourceConfiguration());

        modifiedProperty().setValue(false);
    }

    @Override
    public void save()
    {
        if(modifiedProperty().get())
        {
            //TODO: Save channel text fields to channel
            saveDecoderConfiguration();
            saveEventLogConfiguration();
            saveRecordConfiguration();
            saveSourceConfiguration();

            modifiedProperty().set(false);
        }
    }

    protected abstract void setDecoderConfiguration(DecodeConfiguration config);
    protected abstract void saveDecoderConfiguration();
    protected abstract void setEventLogConfiguration(EventLogConfiguration config);
    protected abstract void saveEventLogConfiguration();
    protected abstract void setRecordConfiguration(RecordConfiguration config);
    protected abstract void saveRecordConfiguration();
    protected abstract void setSourceConfiguration(SourceConfiguration config);
    protected abstract void saveSourceConfiguration();

    private GridPane getTextFieldPane()
    {
        if(mTextFieldPane == null)
        {
            mTextFieldPane = new GridPane();
            mTextFieldPane.setPadding(new Insets(5, 5, 5,5));
            mTextFieldPane.setVgap(5);
            mTextFieldPane.setHgap(5);

            Label systemLabel = new Label("System:");
            GridPane.setHalignment(systemLabel, HPos.RIGHT);
            GridPane.setConstraints(systemLabel, 0, 0);
            mTextFieldPane.getChildren().add(systemLabel);

            GridPane.setConstraints(getSystemField(), 1, 0);
            GridPane.setHgrow(getSystemField(), Priority.ALWAYS);
            mTextFieldPane.getChildren().add(getSystemField());

            Label siteLabel = new Label("Site:");
            GridPane.setHalignment(siteLabel, HPos.RIGHT);
            GridPane.setConstraints(siteLabel, 2, 0);
            mTextFieldPane.getChildren().add(siteLabel);

            GridPane.setConstraints(getSiteField(), 3, 0);
            GridPane.setHgrow(getSiteField(), Priority.ALWAYS);
            mTextFieldPane.getChildren().add(getSiteField());

            Label nameLabel = new Label("Name:");
            GridPane.setHalignment(nameLabel, HPos.RIGHT);
            GridPane.setConstraints(nameLabel, 0, 1);
            mTextFieldPane.getChildren().add(nameLabel);

            GridPane.setConstraints(getNameField(), 1, 1);
            GridPane.setHgrow(getNameField(), Priority.ALWAYS);
            mTextFieldPane.getChildren().add(getNameField());

            Label aliasListLabel = new Label("Alias List:");
            GridPane.setHalignment(aliasListLabel, HPos.RIGHT);
            GridPane.setConstraints(aliasListLabel, 2, 1);
            mTextFieldPane.getChildren().add(aliasListLabel);

            GridPane.setConstraints(getAliasListComboBox(), 3, 1);
            GridPane.setHgrow(getAliasListComboBox(), Priority.ALWAYS);
            mTextFieldPane.getChildren().add(getAliasListComboBox());
        }

        return mTextFieldPane;
    }

    protected TextField getSystemField()
    {
        if(mSystemField == null)
        {
            mSystemField = new TextField();
            mSystemField.setMaxWidth(Double.MAX_VALUE);
            mSystemField.textProperty().addListener(mEditorModificationListener);
        }

        return mSystemField;
    }

    protected TextField getSiteField()
    {
        if(mSiteField == null)
        {
            mSiteField = new TextField();
            mSiteField.setMaxWidth(Double.MAX_VALUE);
            mSiteField.textProperty().addListener(mEditorModificationListener);
        }

        return mSiteField;
    }

    protected TextField getNameField()
    {
        if(mNameField == null)
        {
            mNameField = new TextField();
            mNameField.setMaxWidth(Double.MAX_VALUE);
            mNameField.textProperty().addListener(mEditorModificationListener);
        }

        return mNameField;
    }

    protected ComboBox<String> getAliasListComboBox()
    {
        if(mAliasListComboBox == null)
        {
            mAliasListComboBox = new ComboBox<>();
            mAliasListComboBox.setMaxWidth(Double.MAX_VALUE);
            mAliasListComboBox.getItems().addAll(mAliasModel.getListNames());
        }

        return mAliasListComboBox;
    }

    private VBox getButtonBox()
    {
        if(mButtonBox == null)
        {
            mButtonBox = new VBox();
            mButtonBox.setSpacing(5);
            mButtonBox.setPadding(new Insets(5, 5, 5, 5));
            mButtonBox.getChildren().addAll(getSaveButton(), getResetButton());
        }

        return mButtonBox;
    }

    private Button getSaveButton()
    {
        if(mSaveButton == null)
        {
            mSaveButton = new Button("Save");
            mSaveButton.setMaxWidth(Double.MAX_VALUE);
            mSaveButton.disableProperty().bind(modifiedProperty().not());
        }

        return mSaveButton;
    }

    private Button getResetButton()
    {
        if(mResetButton == null)
        {
            mResetButton = new Button("Reset");
            mResetButton.setMaxWidth(Double.MAX_VALUE);
            mResetButton.disableProperty().bind(modifiedProperty().not());
        }

        return mResetButton;
    }


    /**
     * Simple string change listener that sets the editor modified flag to true any time text fields are edited.
     */
    public class EditorModificationListener implements ChangeListener<String>
    {
        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
        {
            modifiedProperty().set(true);
        }
    }

    /**
     * Alias list change listener to update the contents of the alias list combo box in this editor
     */
    public class AliasModelChangeListener implements Listener<AliasEvent>
    {
        @Override
        public void receive(AliasEvent aliasEvent)
        {
            Platform.runLater(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        List<String> aliasListNames = mAliasModel.getListNames();

                        String selected = getAliasListComboBox().getSelectionModel().getSelectedItem();
                        boolean modified = modifiedProperty().get();

                        if(selected != null && !aliasListNames.contains(selected))
                        {
                            aliasListNames.add(selected);
                        }

                        Collections.sort(aliasListNames);

                        getAliasListComboBox().getItems().clear();
                        getAliasListComboBox().getItems().addAll(aliasListNames);

                        if(selected != null)
                        {
                            getAliasListComboBox().getSelectionModel().select(selected);
                        }

                        //Restore the state of the modified flag to what it was before we updated the combo box
                        modifiedProperty().set(modified);
                    }
                    catch(Throwable t)
                    {
                        mLog.error("Error refreshing alias list names in channel configuration editor");
                    }
                }
            });
        }
    }
}
