# 0009: The chosen method is current for every workspace

**Status:** accepted

## Context

Rikr has six workspaces, and each used to pick its own subject: the tree opened a class, References showed what
was last asked of it, Flow drew the last graph requested. After a few steps nothing on screen said which method the
person was working on, and the status bar repeated the last log line beside an unrelated view.

## Decision

Choosing a method anywhere (the project tree, Go to, a graph, a context menu) makes it the current method, through
`ClassViewer.methodSelected`. The code view's heading names it after its class, the status bar's left slot names
the view and its subject, and workspaces that can follow do: References when "Follow the open class" is on, and a
control-flow graph that is already showing (or when the Flow workspace is next opened, so no graph is built for
clicks made elsewhere). A call graph is not replaced, since the person built it by expanding calls, and the
simulator is not restarted. Log messages take the status bar for a few seconds, then the context returns.

## Consequences

- Opening a class, or a field, clears the current method; `CodeView.display` does this so no caller can forget.
- Any new way of choosing a method calls `methodSelected`, not the workspaces one by one.
- Workspaces follow lazily: `FlowPanel.methodOpened` records a pending graph while the panel is hidden, and
  `ClassViewer.showWorkspace` shows it. New workspaces that follow should do the same.
