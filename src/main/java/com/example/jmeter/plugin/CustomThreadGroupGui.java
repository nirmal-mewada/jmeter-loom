//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.example.jmeter.plugin;

import javax.swing.*;


import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.control.gui.LoopControlPanel;
import org.apache.jmeter.gui.JBooleanPropertyEditor;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.BooleanProperty;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.threads.AbstractThreadGroupSchema;
import org.apache.jmeter.threads.ThreadGroupSchema;
import org.apache.jmeter.threads.gui.AbstractThreadGroupGui;
import org.apache.jmeter.util.JMeterUtils;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import net.miginfocom.swing.MigLayout;

public class CustomThreadGroupGui extends AbstractThreadGroupGui implements ItemListener {
    private static final long serialVersionUID = 240L;
    private LoopControlPanel loopPanel;
    private static final String THREAD_NAME = "Thread Field";
    private static final String RAMP_NAME = "Ramp Up Field";
    private final JTextField threadInput;
    private final JTextField rampInput;
    private final boolean showDelayedStart;
    private JBooleanPropertyEditor delayedStart;
    private final JCheckBox scheduler;
    private final JTextField duration;
    private final JLabel durationLabel;
    private final JTextField delay;
    private final JLabel delayLabel;
    private final JBooleanPropertyEditor sameUserBox;

    public CustomThreadGroupGui() {
        this(true);
    }

    public CustomThreadGroupGui(boolean showDelayedStart) {
        this.threadInput = new JTextField();
        this.rampInput = new JTextField();
        this.scheduler = new JCheckBox(JMeterUtils.getResString("scheduler"));
        this.duration = new JTextField();
        this.durationLabel = JMeterUtils.labelFor(this.duration, "duration");
        this.delay = new JTextField();
        this.delayLabel = JMeterUtils.labelFor(this.delay, "delay");
        this.sameUserBox = new JBooleanPropertyEditor(AbstractThreadGroupSchema.INSTANCE.getSameUserOnNextIteration(), JMeterUtils.getResString("threadgroup_same_user"));
        this.showDelayedStart = showDelayedStart;
        this.init();
        this.initGui();
    }

    public TestElement createTestElement() {
        CustomThreadGroup tg = new CustomThreadGroup();
        this.modifyTestElement(tg);
        return tg;
    }

    public void modifyTestElement(TestElement tg) {
        super.configureTestElement(tg);
        if (tg instanceof AbstractThreadGroup) {
            ((AbstractThreadGroup)tg).setSamplerController((LoopController)this.loopPanel.createTestElement());
        }

        tg.set(AbstractThreadGroupSchema.INSTANCE.getNumThreads(), this.threadInput.getText());
        tg.setProperty("ThreadGroup.ramp_time", this.rampInput.getText());
        if (this.showDelayedStart) {
            this.delayedStart.updateElement(tg);
        }

        tg.setProperty(new BooleanProperty("ThreadGroup.scheduler", this.scheduler.isSelected()));
        tg.setProperty("ThreadGroup.duration", this.duration.getText());
        tg.setProperty("ThreadGroup.delay", this.delay.getText());
        this.sameUserBox.updateElement(tg);
    }

    public void configure(TestElement tg) {
        super.configure(tg);
        this.threadInput.setText(tg.getString(AbstractThreadGroupSchema.INSTANCE.getNumThreads()));
        this.rampInput.setText(tg.getPropertyAsString("ThreadGroup.ramp_time"));
        this.loopPanel.configure((TestElement)tg.getProperty("ThreadGroup.main_controller").getObjectValue());
        if (this.showDelayedStart) {
            this.delayedStart.updateUi(tg);
        }

        this.scheduler.setSelected(tg.getPropertyAsBoolean("ThreadGroup.scheduler"));
        this.toggleSchedulerFields(this.scheduler.isSelected());
        this.duration.setText(tg.getPropertyAsString("ThreadGroup.duration"));
        this.delay.setText(tg.getPropertyAsString("ThreadGroup.delay"));
        this.sameUserBox.updateUi(tg);
    }

    public void itemStateChanged(ItemEvent ie) {
        if (ie.getItem().equals(this.scheduler)) {
            this.toggleSchedulerFields(this.scheduler.isSelected());
        }

    }

    private void toggleSchedulerFields(boolean enable) {
        this.duration.setEnabled(enable);
        this.durationLabel.setEnabled(enable);
        this.delay.setEnabled(enable);
        this.delayLabel.setEnabled(enable);
    }

    private JPanel createControllerPanel() {
        this.loopPanel = new LoopControlPanel(false);
        LoopController looper = (LoopController)this.loopPanel.createTestElement();
        looper.setLoops(1);
        this.loopPanel.configure(looper);
        return this.loopPanel;
    }

    public String getLabelResource() {
        return "threadgroup";
    }

    public void clearGui() {
        super.clearGui();
        this.initGui();
    }

    private void initGui() {
        this.threadInput.setText("1");
        this.rampInput.setText("1");
        this.loopPanel.clearGui();
        if (this.showDelayedStart) {
            this.delayedStart.reset();
        }

        this.scheduler.setSelected(false);
        this.delay.setText("");
        this.duration.setText("");
        this.sameUserBox.reset();
    }

    private void init() {
        JPanel threadPropsPanel = new JPanel(new MigLayout("fillx, wrap 2", "[][fill,grow]"));
        threadPropsPanel.setBorder(BorderFactory.createTitledBorder("Virtual Thread Properties"));
        threadPropsPanel.add(JMeterUtils.labelFor(this.threadInput, "number_of_threads"));
        this.threadInput.setName("Thread Field");
        threadPropsPanel.add(this.threadInput);
        threadPropsPanel.add(JMeterUtils.labelFor(this.rampInput, "ramp_up"));
        this.rampInput.setName("Ramp Up Field");
        threadPropsPanel.add(this.rampInput);
        LoopControlPanel loopController = (LoopControlPanel)this.createControllerPanel();
        threadPropsPanel.add(loopController.getLoopsLabel(), "split 2");
        threadPropsPanel.add(loopController.getInfinite(), "gapleft push");
        threadPropsPanel.add(loopController.getLoops());
        threadPropsPanel.add(this.sameUserBox, "span 2");
        if (this.showDelayedStart) {
            this.delayedStart = new JBooleanPropertyEditor(ThreadGroupSchema.INSTANCE.getDelayedStart(), JMeterUtils.getResString("delayed_start"));
            threadPropsPanel.add(this.delayedStart, "span 2");
        }

        this.scheduler.addItemListener(this);
        threadPropsPanel.add(this.scheduler, "span 2");
        threadPropsPanel.add(this.durationLabel);
        threadPropsPanel.add(this.duration);
        threadPropsPanel.add(this.delayLabel);
        threadPropsPanel.add(this.delay);
        this.add(threadPropsPanel, "Center");
    }

    @Override
    public String getStaticLabel() {
        return "Virtual Thread Group";
    }
}
