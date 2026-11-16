import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import vm from 'node:vm'

// Exercise the JavaScript/host contract without requiring a platform UI.
const source = readFileSync(new URL('../javascript/index.js', import.meta.url), 'utf8')
const tracks = Array.from({length: 180}, (_, i) => ({id: `local:${i}`, title: `Song ${i}`, artist: 'Artist', album: '', duration: 180000, artwork: ''}))
const actions = new Map(), events = new Map(), calls = [], pages = []
let tree, patch, pendingSearch, disposed = 0
const host = {
  playerState: () => ({track: tracks[0], playing: false, position: 0, queue: tracks}),
  playerCapabilities: () => ['toggle', 'previous', 'next', 'seek', 'playTracks', 'addToQueue', 'queueIndex'],
  action: (id, fn) => actions.set(id, fn),
  on: (id, fn) => { events.set(id, fn); return () => { events.delete(id); disposed++ } },
  ui: (_, value) => {
    let count = 0
    const ids = new Set()
    function walk(node, depth = 0) {
      assert.ok(depth <= 32); assert.ok(++count <= 2000)
      assert.ok(!ids.has(node.id)); ids.add(node.id)
      assert.ok(Object.values(node.props).every(value => typeof value === 'string'))
      node.children.forEach(child => walk(child, depth + 1))
    }
    walk(value); tree = value
  },
  patchMany: changes => { patch = changes },
  playTracks: async (list, index = 0) => calls.push(['playTracks', list, index]),
  addToQueue: async list => calls.push(['addToQueue', list]),
  player: async (...args) => calls.push(args),
  navigate: async destination => calls.push(['navigate', destination]),
  library: async (offset, limit) => { pages.push(offset); return tracks.slice(offset, offset + limit) },
  call: async (name, request) => {
    if (name === 'ui.home/v1') return {shelves: Array.from({length: 24}, () => ({title: 'Shelf', items: tracks.slice(0, 20).map(track => ({track}))}))}
    if (name === 'ui.search/v1') return new Promise(resolve => { pendingSearch = resolve })
    return {item: {tracks}}
  }
}
const context = vm.createContext({})
vm.runInContext(source, context)
await context.plugin.init(host)
await actions.get('page-library')()
await actions.get('more')()
await actions.get('back')()
assert.deepEqual(pages, [0, 50, 0])
await actions.get('page-queue')()
function find(node, id) {
  if (node.id === id) return node
  for (const child of node.children) { const result = find(child, id); if (result) return result }
}
await actions.get(find(tree, 'wide-play-0').props.action)()
assert.deepEqual(calls.pop(), ['queueIndex', 0])
await actions.get('seek')('0.5')
assert.deepEqual(calls.at(-1), ['seek', 90000])
await actions.get('seek')('NaN')
assert.equal(calls.length, 1)
await events.get('playback_changed')({...host.playerState(), playing: true, position: 12000})
assert.equal(patch['compact-elapsed'].text, '0:12')
const search = actions.get('search')('slow request')
await actions.get('page-library')()
pendingSearch({results: {tracks: [tracks[100]]}})
await search
const allText = JSON.stringify(tree)
assert.ok(allText.includes('Your library'))
assert.ok(!allText.includes('Song 100'))
await actions.get('settings')()
assert.deepEqual(calls.at(-1), ['navigate', 'settings'])
context.plugin.dispose()
assert.equal(disposed, 3)
assert.equal(events.size, 0)
console.log('MetroFlow host contract checks passed (paging, stale responses, playback, disposal, tree limits).')
