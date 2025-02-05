<script setup lang="ts">
import { useGraphStore, type Node } from '@/stores/graph'
import { useProjectStore } from '@/stores/project'
import { useSuggestionDbStore } from '@/stores/suggestionDatabase'
import type { Highlighter } from '@/util/codemirror'
import { colorFromString } from '@/util/colors'
import { usePointer } from '@/util/events'
import { useLocalStorage } from '@vueuse/core'
import { computed, onMounted, ref, watchEffect } from 'vue'
import * as Y from 'yjs'
import { qnJoin, tryQualifiedName } from '../util/qualifiedName'
import { unwrap } from '../util/result'

// Use dynamic imports to aid code splitting. The codemirror dependency is quite large.
const {
  bracketMatching,
  foldGutter,
  highlightSelectionMatches,
  minimalSetup,
  EditorState,
  EditorView,
  yCollab,
  syntaxHighlighting,
  defaultHighlightStyle,
  tooltips,
  enso,
  hoverTooltip,
} = await import('@/util/codemirror')

const projectStore = useProjectStore()
const graphStore = useGraphStore()
const suggestionDbStore = useSuggestionDbStore()
const rootElement = ref<HTMLElement>()

// == CodeMirror editor setup  ==

const editorView = new EditorView()
watchEffect(() => {
  const module = projectStore.module
  if (!module) return
  const yText = module.doc.contents
  const undoManager = module.undoManager
  const awareness = projectStore.awareness
  editorView.setState(
    EditorState.create({
      doc: yText.toString(),
      extensions: [
        minimalSetup,
        yCollab(yText, awareness, { undoManager }),
        syntaxHighlighting(defaultHighlightStyle as Highlighter),
        bracketMatching(),
        foldGutter(),
        highlightSelectionMatches(),
        tooltips({ position: 'absolute' }),
        hoverTooltip((ast) => {
          const dom = document.createElement('div')
          const ydoc = projectStore.module?.doc.ydoc
          if (ydoc == null) return
          const start = ast.whitespaceStartInCodeParsed + ast.whitespaceLengthInCodeParsed
          const end = start + ast.childrenLengthInCodeParsed
          let foundNode: Node | undefined
          for (const node of graphStore.nodes.values()) {
            const nodeStart = Y.createAbsolutePositionFromRelativePosition(node.docRange[0], ydoc)
              ?.index
            if (nodeStart == null || nodeStart > start) continue
            const nodeEnd = Y.createAbsolutePositionFromRelativePosition(node.docRange[1], ydoc)
              ?.index
            if (nodeEnd == null || nodeEnd < end) continue
            foundNode = node
            break
          }
          if (foundNode == null) return
          const expressionInfo = projectStore.computedValueRegistry.getExpressionInfo(
            foundNode.rootSpan.id,
          )
          if (expressionInfo == null) return
          dom
            .appendChild(document.createElement('div'))
            .appendChild(document.createTextNode(`AST ID: ${foundNode.rootSpan.id}`))
          dom
            .appendChild(document.createElement('div'))
            .appendChild(document.createTextNode(`Type: ${expressionInfo.typename ?? 'Unknown'}`))
          const method = expressionInfo?.methodCall?.methodPointer
          if (method != null) {
            const moduleName = tryQualifiedName(method.module)
            const methodName = tryQualifiedName(method.name)
            const qualifiedName = qnJoin(unwrap(moduleName), unwrap(methodName))
            const [id] = suggestionDbStore.entries.nameToId.lookup(qualifiedName)
            const suggestionEntry = id != null ? suggestionDbStore.entries.get(id) : undefined
            if (suggestionEntry != null) {
              const groupNode = dom.appendChild(document.createElement('div'))
              groupNode.appendChild(document.createTextNode('Group: '))
              const groupNameNode = groupNode.appendChild(document.createElement('span'))
              groupNameNode.appendChild(document.createTextNode(`${method.module}.${method.name}`))
              groupNameNode.style.color =
                suggestionEntry?.groupIndex != null
                  ? `var(--group-color-${suggestionDbStore.groups[suggestionEntry.groupIndex]
                      ?.name})`
                  : colorFromString(expressionInfo?.typename ?? 'Unknown')
            }
          }
          return { dom }
        }),
        enso(),
      ],
    }),
  )
})

onMounted(() => {
  editorView.focus()
  rootElement.value?.prepend(editorView.dom)
})

const editorSize = useLocalStorage<{ width: number | null; height: number | null }>(
  'code-editor-size',
  { width: null, height: null },
)

let initSize = { width: 0, height: 0 }
const resize = usePointer((pos, _, type) => {
  if (rootElement.value == null) return
  if (type == 'start') initSize = rootElement.value.getBoundingClientRect()
  editorSize.value.width = initSize.width + pos.relative.x
  editorSize.value.height = initSize.height - pos.relative.y
})

function resetSize() {
  editorSize.value.width = null
  editorSize.value.height = null
}

const editorStyle = computed(() => {
  return {
    width: editorSize.value.width ? `${editorSize.value.width}px` : '50%',
    height: editorSize.value.height ? `${editorSize.value.height}px` : '30%',
  }
})
</script>

<template>
  <div
    ref="rootElement"
    class="CodeEditor"
    :style="editorStyle"
    @keydown.enter.stop
    @wheel.stop.passive
    @pointerdown.stop
  >
    <div class="resize-handle" v-on="resize.events" @dblclick="resetSize">
      <svg viewBox="0 0 16 16">
        <circle cx="2" cy="2" r="1.5" />
        <circle cx="8" cy="2" r="1.5" />
        <circle cx="8" cy="8" r="1.5" />
        <circle cx="14" cy="2" r="1.5" />
        <circle cx="14" cy="8" r="1.5" />
        <circle cx="14" cy="14" r="1.5" />
      </svg>
    </div>
  </div>
</template>

<style>
.CodeEditor {
  position: absolute;
  bottom: 5px;
  left: 5px;
  width: 50%;
  height: 30%;
  max-width: calc(100% - 10px);
  max-height: calc(100% - 10px);

  &::before {
    content: '';
    display: inline-block;
    position: absolute;
    width: 100%;
    height: 100%;
    backdrop-filter: blur(16px);
  }

  &.v-enter-active,
  &.v-leave-active {
    transition:
      transform 0.2s ease,
      opacity 0.2s ease;
  }

  &.v-enter-from,
  &.v-leave-to {
    transform: scale(95%);
    opacity: 0;
  }
}

.resize-handle {
  position: absolute;
  top: -3px;
  right: -3px;
  width: 20px;
  height: 20px;
  padding: 5px;
  /* cursor: nesw-resize; */

  svg {
    fill: black;
    width: 100%;
    height: 100%;
    opacity: 0.1;
    transition: opacity 0.1s ease-in-out;
  }

  &:hover svg {
    opacity: 0.9;
  }
}

.CodeEditor :is(.cm-editor) {
  position: relative;
  color: white;
  width: 100%;
  height: 100%;
  background-color: rgba(255, 255, 255, 0.35);
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 5px;
  opacity: 1;
  color: black;
  text-shadow: 0 0 2px rgba(255, 255, 255, 0.4);
  font-size: 12px;
  outline: 1px solid transparent;
  transition: outline 0.1s ease-in-out;
}

.CodeEditor :is(.cm-focused) {
  outline: 1px solid rgba(0, 0, 0, 0.5);
}

.CodeEditor :is(.cm-tooltip-hover) {
  padding: 4px;
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.4);
  text-shadow: 0 0 2px rgba(255, 255, 255, 0.4);

  &::before {
    content: '';
    background-color: rgba(255, 255, 255, 0.35);
    backdrop-filter: blur(64px);
  }
}
</style>
