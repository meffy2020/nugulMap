const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const vm = require('node:vm')
const ts = require('typescript')

// Execute the map's actual data loader with a minimal map/React adapter.
// Discovery APIs are deliberately absent: any new dependency fails the test.
function mount(fetchZones, mapReady = true, initialStates = {}) {
  const stateValues = { ...initialStates }
  const updates = []
  const callbacks = []
  const effects = []
  let imperative
  let stateIndex = 0
  const point = (lat, lng) => ({ getLat: () => lat, getLng: () => lng })
  const map = {
    getBounds: () => ({ getSouthWest: () => point(37.5, 127), getNorthEast: () => point(37.6, 127.1) }),
    setCenter: (point) => updates.push(['center', point]),
    setLevel: () => {},
  }
  const react = {
    forwardRef: (render) => render,
    useState: (value) => {
      const id = stateIndex++
      if (!(id in stateValues)) stateValues[id] = id === 6 && mapReady ? map : value
      return [stateValues[id], (next) => { stateValues[id] = next; updates.push([id, next]) }]
    },
    useRef: (current) => ({ current }),
    useCallback: (callback) => { callbacks.push(callback); return callback },
    useEffect: (effect) => { effects.push(effect) },
    useImperativeHandle: (_, factory) => { imperative = factory() },
  }
  const exports = {}
  const source = fs.readFileSync(require.resolve('../components/map-container.tsx'), 'utf8')
  const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText
  const modules = {
    react,
    'react/jsx-runtime': { jsx: (type, props) => ({ type, props }), jsxs: (type, props) => ({ type, props }) },
    '@/lib/api': { fetchZones },
    '@/lib/utils': { cn: () => '' },
    '@/components/ui/button': { Button: 'button' },
    'lucide-react': {},
    'next/script': {},
    'next/image': {},
    vaul: { Drawer: { Title: 'Drawer.Title', Description: 'Drawer.Description' } },
  }
  vm.runInNewContext(output, {
    exports, process: { env: {} },
    require: (name) => { assert.ok(name in modules, `Unexpected map dependency: ${name}`); return modules[name] },
    window: { kakao: { maps: { LatLng: function(lat, lng) { this.lat = lat; this.lng = lng } } }, setTimeout: () => {} },
  })
  const render = () => { stateIndex = 0; return exports.MapContainer({}, null) }
  render()
  return { load: callbacks[0], updates, imperative, render }
}

test('visible bounds load only smoking zones and commit them', async () => {
  let requested
  const zones = [{ id: 1, latitude: 37.55, longitude: 127.05 }]
  const map = mount(async (...bounds) => { requested = bounds; return zones })
  await map.load()
  assert.deepEqual(requested, [37.5, 37.6, 127, 127.1])
  assert.ok(map.updates.some(([id, value]) => id === 1 && value === zones))
  assert.ok(map.updates.some(([id, value]) => id === 3 && value === 'ready'))
})

test('failed refresh exposes retry state and preserves existing zones', async () => {
  const map = mount(async () => { throw Error('offline') })
  await map.load()
  assert.ok(map.updates.some(([id, value]) => id === 3 && value === 'error'))
  assert.equal(map.updates.some(([id]) => id === 1), false)
})

test('late response cannot overwrite a newer map viewport', async () => {
  const pending = []
  const map = mount(() => new Promise((resolve) => pending.push(resolve)))
  const first = map.load()
  const second = map.load()
  const newest = [{ id: 2 }]
  pending[1](newest)
  await second
  pending[0]([{ id: 1 }])
  await first
  assert.deepEqual(map.updates.filter(([id]) => id === 1), [[1, newest]])
})

test('invalid location cannot move the map', () => {
  const map = mount(async () => [])
  map.imperative.centerOnLocation(NaN, 127)
  map.imperative.centerOnLocation(91, 127)
  assert.equal(map.updates.length, 0)
  map.imperative.centerOnLocation(37.5, 127)
  assert.ok(map.updates.some(([id]) => id === 'center'))
})

test('launch entry points cannot call retired discovery APIs', () => {
  const paths = ['../app/page.tsx', '../components/map-container.tsx', '../../mobile/App.tsx']
  for (const path of paths) {
    const source = fs.readFileSync(require.resolve(path), 'utf8')
    const tree = ts.createSourceFile(path, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
    const calls = []
    function visit(node) {
      if (ts.isCallExpression(node)) calls.push(node.expression.getText(tree))
      ts.forEachChild(node, visit)
    }
    visit(tree)
    assert.equal(calls.some((name) => /Insight|Recommend|LocationLetter/.test(name)), false, path)
  }
})

function elements(tree) {
  if (!tree || typeof tree !== 'object') return []
  return [tree, ...[tree.props?.children].flat().flatMap(elements)]
}

test('empty status appears only after a successful empty zone response', async () => {
  let resolve
  const map = mount(() => new Promise((done) => { resolve = done }), true, { 2: false })
  const statuses = () => elements(map.render()).filter((node) => node.props?.role === 'status')
  assert.equal(statuses().length, 0)
  const request = map.load()
  assert.equal(statuses().length, 0)
  resolve([])
  await request
  assert.equal(statuses().length, 1)
  assert.equal(statuses()[0].props.children, '현재 지도에 등록된 흡연구역이 없습니다.')
  assert.match(statuses()[0].props.className, /pointer-events-none/)
})

test('failed zone request exposes an error rather than an empty result', async () => {
  const map = mount(async () => { throw Error('offline') }, true, { 2: false })
  await map.load()
  const nodes = elements(map.render())
  assert.equal(nodes.some((node) => node.props?.role === 'status'), false)
  assert.equal(nodes.some((node) => node.props?.role === 'alert'), true)
})

test('nonempty zone response does not display an empty status', async () => {
  const map = mount(async () => [{ id: 1 }], true, { 2: false })
  await map.load()
  assert.equal(elements(map.render()).some((node) => node.props?.role === 'status'), false)
})

test('place detail exposes its title and address through Drawer accessibility components', () => {
  const map = mount(async () => [], true, { 0: { id: 1, name: '테스트 흡연구역', address: '서울 종로구' } })
  const nodes = elements(map.render())
  const title = nodes.find((node) => node.type === 'Drawer.Title')
  const description = nodes.find((node) => node.type === 'Drawer.Description')
  assert.equal(title?.props.asChild, true)
  assert.equal(title?.props.children.props.children, '테스트 흡연구역')
  assert.equal(description?.props.asChild, true)
  assert.equal(description?.props.children.props.children, '서울 종로구')
})

for (const [label, states] of [
  ['map still loading', { 2: true, 3: 'ready' }],
  ['map SDK failed', { 2: false, 3: 'ready', 5: true }],
]) {
  test(`empty status stays hidden while ${label}`, () => {
    const map = mount(async () => [], true, states)
    assert.equal(elements(map.render()).some((node) => node.props?.role === 'status'), false)
  })
}
