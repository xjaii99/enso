<script setup lang="ts">
import { spanKindName, type Span } from '@/stores/graph'
import { useResizeObserver } from '@/util/events'
import { Rect } from '@/util/rect'
import { Vec2 } from '@/util/vec2'
import type { ExprId } from 'shared/yjsModel'
import { computed, onUpdated, ref, shallowRef, watch } from 'vue'

const props = defineProps<{
  content: string
  span: Span
  offset: number
}>()

const emit = defineEmits<{
  updateExprRect: [expr: ExprId, rect: Rect]
}>()

const spanClass = computed(() => spanKindName(props.span.kind))

const exprPart = computed(() => {
  return props.content.substring(props.offset, props.offset + props.span.length)
})

const rootNode = ref<HTMLElement>()
const nodeSize = useResizeObserver(rootNode, false)

const exprRect = shallowRef<Rect>()

function updateRect() {
  let domNode = rootNode.value

  if (domNode == null) return
  const pos = new Vec2(domNode.offsetLeft, domNode.offsetTop)
  const size = nodeSize.value
  const rect = new Rect(pos, size)
  if (exprRect.value != null && rect.equals(exprRect.value)) return
  exprRect.value = rect
}

const childOffsets = computed(() => {
  let offset = props.offset
  return props.span.children.map((child) => {
    const start = offset
    offset += child.length
    return start
  })
})

watch(nodeSize, updateRect)

onUpdated(() => {
  updateRect()
})

watch(exprRect, (rect) => {
  if (rect == null) return
  emit('updateExprRect', props.span.id, rect)
})
</script>

<template>
  <span
    ref="rootNode"
    :class="['Span', spanClass]"
    style="{ transform }"
    :data-span-id="props.span.id"
    :data-span-start="props.offset"
    ><template v-if="props.span.children.length > 0"
      ><NodeSpan
        v-for="(child, index) in props.span.children"
        :key="child.id"
        :content="props.content"
        :span="child"
        :offset="childOffsets[index]!"
        @updateExprRect="(id, rect) => emit('updateExprRect', id, rect)" /></template
    ><template v-else>{{ exprPart }}</template></span
  >
</template>

<style scoped>
.Span {
  color: white;
  white-space: pre;
  align-items: center;
  transition: background 0.2s ease;

  &.Root {
    color: white;
  }

  &.Token {
    color: rgb(255 255 255 / 0.33);
  }

  &.Literal {
    font-weight: bold;
  }

  &.Ident {
    background-color: var(--node-color-port);
    border-radius: var(--node-border-radius);
    margin: -2px -4px;
    padding: 2px 4px;
  }
}
</style>
