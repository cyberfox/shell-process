/**
  Copyright 2011 Google Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/
package com.cyberfox.idea.plugins.shellprocess;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.JOptionPane;

/**
 * @author Morgan Schweers
 */
public class ShellProcess extends EditorAction {
  private static final Key<String> DEFAULT_COMMAND_KEY =
      Key.create("SHELL_PROCESS_DEFAULT_COMMAND");

  protected ShellProcess() {
    super(new AltHandler());
  }

  private static class AltHandler extends EditorActionHandler {
    @Override
    protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      final Document doc = editor.getDocument();
      String priorCmd = getPriorCommand(editor);

      SelectionModel selected = editor.getSelectionModel();
      boolean hasSelection = selected.hasSelection();
      Project currentProject = editor.getProject();
      if(hasSelection) {
        int startAt = selected.getSelectionStart();
        int endAt = selected.getSelectionEnd();
        String fixup = selected.getSelectedText();
        String command = JOptionPane.showInputDialog(
                "Shell command to run on selected lines:", priorCmd);
        if(command != null && !command.equals("")) {
          saveCommand(editor, command);
          String replacement = process(command, fixup);
          if(replacement != null) {
            Runnable r = () -> doc.replaceString(startAt, endAt, replacement);
            WriteCommandAction.runWriteCommandAction(currentProject, r);
          }
        }
      } else {
        String command = JOptionPane.showInputDialog(
                "Shell command to insert at current location:", priorCmd);
        if(command != null && !command.equals("")) {
          String insertion = process(command, "");
          if(insertion != null) {
            Runnable r = () -> doc.insertString(editor.getCaretModel().getOffset(), insertion);
            WriteCommandAction.runWriteCommandAction(currentProject, r);
          }
        }
      }
    }

    private void saveCommand(Editor editor, String command) {
      Project currentProject = editor.getProject();

      if(currentProject != null) {
        currentProject.putUserData(DEFAULT_COMMAND_KEY, command);
      }
    }

    private String getPriorCommand(Editor editor) {
      Project currentProject = editor.getProject();
      String priorCmd = null;

      if(currentProject != null) {
        priorCmd = currentProject.getUserData(DEFAULT_COMMAND_KEY);
      }

      if(priorCmd == null || priorCmd.equals("")) {
        priorCmd = "sort";
      }
      return priorCmd;
    }

    private String process(String command, String filterContent) {
      try {
        String[] cmd = {"/bin/sh", "-c", command};
        Process p = Runtime.getRuntime().exec(cmd);
        DataInputStream pStdout = new DataInputStream(p.getInputStream());
        DataOutputStream pStdin = new DataOutputStream(p.getOutputStream());

        pStdin.write(filterContent.getBytes());
        pStdin.close();
        return cat(pStdout);
      } catch(Exception e) {
        System.err.println("Got exception: " + e);
      }
      return null;
    }

    public static String cat(InputStream is) throws IOException {
      byte[] buf = new byte[65536];
      int offset = 0;
      int bytesRead;
      while( (bytesRead = is.read(buf, offset, 8192)) != -1) {
        offset += bytesRead;
        if(offset+8192 >= buf.length) {
          byte[] newBuf = new byte[buf.length * 3];
          System.arraycopy(buf, 0, newBuf, 0, offset);
          buf = newBuf;
        }
      }
      return new String(buf, 0, offset);
    }
  }
}

// Test data
// 7
// 5
// 7
// 5
// 2
// 7
// 5
// 4
// 6
// 6
