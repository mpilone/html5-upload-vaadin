
package org.mpilone.vaadin;

import static java.lang.String.format;
import static org.mpilone.vaadin.StyleConstants.FULL_WIDTH;

import java.util.Date;

import org.mpilone.vaadin.upload.AbstractHtml5Uploader;

import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.*;

/**
 *
 * @author mpilone
 */
public abstract class AbstractUploadDemo extends HorizontalLayout implements
    UploadLogger {

  private TextArea logTxt;

  private final VerticalLayout leftColumnLayout;

  public AbstractUploadDemo() {
    //setPollInterval(3000);
    setWidth(FULL_WIDTH);

    HorizontalLayout contentLayout = this;
    contentLayout.setMargin(true);
    contentLayout.setSpacing(true);
    contentLayout.setWidth(FULL_WIDTH);

    // Left column (uploaders)
    leftColumnLayout = new VerticalLayout();
    leftColumnLayout.setSpacing(true);
    contentLayout.addComponent(leftColumnLayout);

    // Right column (log area)
    VerticalLayout rightColumnLayout = new VerticalLayout();
    rightColumnLayout.setSpacing(true);
    contentLayout.addComponent(rightColumnLayout);

    Label lbl = new Label("<h2>Upload Log</h2>", ContentMode.HTML);
    rightColumnLayout.addComponent(lbl);

    logTxt = new TextArea();
    logTxt.setRows(35);
    logTxt.setWidth(FULL_WIDTH);
    rightColumnLayout.addComponent(logTxt);

    Button btn = new Button("Clear Log", new Button.ClickListener() {
      @Override
      public void buttonClick(Button.ClickEvent event) {
        logTxt.setValue("");
      }
    });
    rightColumnLayout.addComponent(btn);
  }

  protected void addExample(String title, AbstractHtml5Uploader upload,
      Component... comps) {
    ProgressBar progressBar = new ProgressBar();
    upload.addProgressListener(new BarProgressListener(progressBar, this));

    Label lbl = new Label("<h2>" + title + "</h2>", ContentMode.HTML);
    leftColumnLayout.addComponent(lbl);

    HorizontalLayout rowLayout = new HorizontalLayout();
    rowLayout.setSpacing(true);
    leftColumnLayout.addComponent(rowLayout);

    rowLayout.addComponent(upload);
    rowLayout.addComponent(progressBar);

    if (comps != null) {
      for (Component c : comps) {
        rowLayout.addComponent(c);
      }
    }
  }

  @Override
  public void log(String msg, Object... args) {

    if (args != null && args.length > 0) {
      msg = format(msg, args);
    }

    String value = logTxt.getValue();
    value = format("%s\n[%s] %s", value, new Date(), msg);
    logTxt.setValue(value);
    logTxt.setCursorPosition(value.length() - 1);
  }

}
