const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const vm = require('node:vm')
const ts = require('typescript')

function compile(path, modules, globals = {}) {
  const exports = {}
  const source = fs.readFileSync(require.resolve(path), 'utf8')
  const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText
  vm.runInNewContext(output, {
    exports, console: { error() {}, warn() {} }, ...globals,
    require: (name) => {
      if (name === 'react/jsx-runtime') return { jsx: (type, props) => ({ type, props }), jsxs: (type, props) => ({ type, props }) }
      assert.ok(name in modules, `Unexpected dependency: ${name}`)
      return modules[name]
    },
  })
  return exports
}
function find(node, type) {
  if (node?.type === type) return node
  for (const child of [node?.props?.children].flat().filter(Boolean)) {
    const found = find(child, type)
    if (found) return found
  }
}
function mountHome() {
  const moves = [], toasts = [], geocodes = [], searches = [], effects = [], locations = []
  let refIndex = 0
  const modules = {
    react: {
      Suspense: 'Suspense',
      useRef: (current) => ({ current: refIndex++ === 0 ? { getCenter: () => ({ lat: 37, lng: 127 }), centerOnLocation: (...args) => moves.push(args) } : current }),
      useEffect: (effect) => effects.push(effect),
    },
    '@/hooks/use-auth': { useAuth: () => ({ user: null }) },
    '@/hooks/use-toast': { useToast: () => ({ toast: (value) => toasts.push(value) }) },
    'next/navigation': { useRouter: () => ({}), useSearchParams: () => ({ get: () => null }) },
    '@/lib/api': { searchZones: (query) => new Promise((resolve, reject) => searches.push({ query, resolve, reject })) },
    'lucide-react': {},
  }
  for (const [path, name] of [
    ['floating-action-button', 'FloatingActionButton'], ['map-container', 'MapContainer'],
    ['current-location-button', 'CurrentLocationButton'], ['floating-user-profile', 'FloatingUserProfile'], ['search-bar', 'SearchBar'],
  ]) modules['@/components/' + path] = { [name]: name }
  const app = compile('../app/page.tsx', modules, {
    navigator: { geolocation: { getCurrentPosition: (...args) => locations.push(args) } },
    window: { kakao: { maps: { services: { Status: { OK: 'OK' }, Places: function() { this.keywordSearch = (query, callback) => geocodes.push({ query, callback }) } } } } },
  })
  const content = app.default().props.children.type()
  return { moves, toasts, geocodes, searches, effects, locations, search: find(content, 'SearchBar').props.onSearch }
}
const flush = () => new Promise((resolve) => setImmediate(resolve))

for (const oldResult of ['success', 'empty']) {
  test(`a delayed old geocode ${oldResult} cannot replace a newer search`, async () => {
    const app = mountHome()
    const old = app.search('first')
    const latest = app.search('second')
    app.geocodes[1].callback([{ x: '127.1', y: '37.6' }], 'OK')
    await latest
    app.geocodes[0].callback(oldResult === 'success' ? [{ x: '126', y: '36' }] : [], oldResult === 'success' ? 'OK' : 'ZERO_RESULT')
    await flush()
    // Resolve unexpected stale fallbacks too, so the failing baseline never hangs.
    app.searches.forEach((search) => search.resolve([]))
    await old
    assert.deepEqual(app.moves, [[37.6, 127.1]])
    assert.equal(app.searches.length, 0)
    assert.equal(app.toasts.length, 0)
  })
}
for (const oldResult of ['success', 'empty', 'error']) {
  test(`a delayed old database ${oldResult} cannot move the map or show a stale toast`, async () => {
    const app = mountHome()
    const old = app.search('first')
    app.geocodes[0].callback([], 'ZERO_RESULT')
    await flush()
    const latest = app.search('second')
    app.geocodes[1].callback([{ x: '127.1', y: '37.6' }], 'OK')
    await latest
    if (oldResult === 'error') app.searches[0].reject(Error('offline'))
    else app.searches[0].resolve(oldResult === 'success' ? [{ latitude: 36, longitude: 126 }] : [])
    await old
    assert.deepEqual(app.moves, [[37.6, 127.1]])
    assert.equal(app.toasts.length, 0)
  })
}

test('latest database failure remains a failure, not an empty result', async () => {
  const app = mountHome()
  const pending = app.search('first')
  app.geocodes[0].callback([], 'ZERO_RESULT')
  await flush()
  app.searches[0].reject(Error('offline'))
  await pending
  assert.equal(app.toasts[0].title, '검색 실패')
})

test('startup location has a finite timeout and cannot override a newer search', async () => {
  const app = mountHome()
  app.effects.forEach((effect) => effect())
  assert.equal(app.locations.length, 1)
  assert.equal(app.locations[0][2]?.timeout, 10000)
  const pending = app.search('second')
  app.geocodes[0].callback([{ x: '127.1', y: '37.6' }], 'OK')
  await pending
  app.locations[0][0]({ coords: { latitude: 36, longitude: 126 } })
  assert.deepEqual(app.moves, [[37.6, 127.1]])
})

test('current-location control is named and requests location with a finite timeout', () => {
  const requests = [], states = []
  const { CurrentLocationButton } = compile('../components/current-location-button.tsx', {
    react: { useState: (initial) => [initial, (value) => states.push(value)] },
    'lucide-react': {}, '@/components/ui/button': { Button: 'Button' },
    '@/hooks/use-toast': { useToast: () => ({ toast() {} }) },
  }, { navigator: { geolocation: { getCurrentPosition: (...args) => requests.push(args) } } })
  const button = CurrentLocationButton({ onLocationFound() {} })
  assert.equal(button.props['aria-label'], '현재 위치로 이동')
  button.props.onClick()
  assert.equal(requests[0][2].timeout, 10000)
  requests[0][1]({ code: 3 })
  assert.deepEqual(states, [true, false])
})
