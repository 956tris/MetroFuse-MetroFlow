// MetroFlow uses the active MetroFuse catalog and player; it supplies no provider.
globalThis.plugin = {
  init(host) {
    const colors = {base: '#0E1420', panel: '#182232', raised: '#273750', text: '#F5F8FE', muted: '#B5C4D9', accent: '#FFCB77'}
    const state = {page: 'home', tracks: [], sidebarTracks: [], shelves: [], results: [], query: '', offset: 0, more: true, loading: false, message: '', player: host.playerState(), expanded: false, filter: 'all', source: {}, catalogResults: {}}
    const capabilities = new Set(host.playerCapabilities())
    let serial = 0
    let request = 0
    let libraryEpoch = 0
    let stopped = false
    const subscriptions = []
    const node = (id, type, props = {}, children = []) => ({id, type, props: Object.fromEntries(Object.entries(props).map(([k, v]) => [k, String(v)])), children})
    const text = (id, value, size = 16, props = {}) => node(id, 'Text', {text: value, color: colors.text, fontSize: size, fontWeight: size >= 20 ? 700 : 400, maxLines: 2, ...props})
    const button = (id, value, action, props = {}) => node(id, 'TextButton', {text: value, action, color: colors.text, fontSize: 14, fontWeight: 600, ...props})
    const icon = (id, name, size = 24, props = {}) => node(id, 'Icon', {icon: name, iconSize: size, color: colors.text, ...props})
    const iconButton = (id, name, label, action, props = {}) => node(id, 'IconButton', {icon: name, description: label, action, color: colors.text, width: 40, height: 40, ...props})
    const time = value => { const seconds = Math.max(0, Math.floor(Number(value || 0) / 1000)); return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}` }
    const title = () => ({home: 'Home', library: 'Your library', search: 'Search', queue: 'Up next', collection: state.collectionTitle || 'Collection'})[state.page]
    const action = (fn) => { const id = `item-${serial++}`; host.action(id, fn); return id }
    const report = () => { state.message = 'Could not load this content. Check your selected source in the original interface, then retry.'; render() }
    const play = tracks => host.playTracks(tracks).catch(report)
    function trackRow(prefix, track, tracks, index) {
      const current = track.id === state.player.track.id
      return node(`${prefix}-row-${index}`, 'Row', {fillWidth: true, background: current ? colors.raised : colors.panel, radius: 8, padding: 8, spacing: 8}, [
        node(`${prefix}-art-${index}`, 'Artwork', {url: track.artwork || '', description: track.title, width: 48, height: 48, radius: 6}),
        node(`${prefix}-meta-${index}`, 'Column', {weight: 1, spacing: 2}, [
          button(`${prefix}-play-${index}`, track.title, action(() => (state.page === 'queue' ? host.player('queueIndex', index) : host.playTracks(tracks, index)).catch(report)), {enabled: capabilities.has(state.page === 'queue' ? 'queueIndex' : 'playTracks'), color: current ? colors.accent : colors.text, maxLines: 1, fillWidth: true}),
          text(`${prefix}-artist-${index}`, track.artist, 12, {color: colors.muted, maxLines: 1})
        ]),
        text(`${prefix}-duration-${index}`, time(track.duration), 12, {color: colors.muted}),
        iconButton(`${prefix}-add-${index}`, 'add', 'Add to queue', action(() => host.addToQueue([track]).catch(report)), {enabled: capabilities.has('addToQueue'), iconSize: 18, color: colors.muted})
      ])
    }
    async function collection(kind, item) {
      const ticket = ++request
      state.collection = {kind, item}
      state.page = 'collection'; state.collectionTitle = item.title || item.name; state.results = []; state.loading = true; state.message = ''; render()
      try {
        const response = await host.call('ui.collection/v1', {kind, id: item.id})
        if (stopped || ticket !== request) return
        state.results = response.item?.tracks || []; state.collection.item = response.item || item
        if (!response.item) state.message = 'This collection is unavailable from the active source.'
      } catch (_) { if (ticket === request) state.message = 'Could not open this collection. Try again from Home.' }
      if (stopped || ticket !== request) return
      state.loading = false; render()
    }
    function shelf(prefix, shelf, shelfIndex, wide) {
      const size = wide ? 168 : 148
      return node(`${prefix}-shelf-${shelfIndex}`, 'Column', {spacing: 10}, [
        text(`${prefix}-shelf-title-${shelfIndex}`, shelf.title, 22),
        node(`${prefix}-cards-${shelfIndex}`, 'LazyRow', {height: size + 70, spacing: 16}, shelf.items.map((entry, index) => {
          const kind = ['track', 'album', 'artist', 'playlist'].find(key => entry[key])
          if (!kind) return node(`${prefix}-unknown-${shelfIndex}-${index}`, 'Spacer')
          const item = entry[kind]; const id = `${prefix}-card-${shelfIndex}-${index}`
          return node(id, 'Column', {width: size, spacing: 8, action: action(() => kind === 'track' ? play([item]) : collection(kind, item))}, [
            node(`${id}-art`, 'Artwork', {url: item.artwork || '', description: item.title || item.name, width: size, height: size, radius: kind === 'artist' ? 64 : 4}),
            text(`${id}-open`, item.title || item.name, 15, {maxLines: 1, fontWeight: 600, fillWidth: true}),
            text(`${id}-subtitle`, item.artist || item.author || '', 12, {color: colors.muted, maxLines: 1})
          ])
        }))
      ])
    }
    function navigation(prefix, wide) {
      const children = wide ? [node(`${prefix}-library-heading`, 'Row', {fillWidth: true, spacing: 12, action: 'page-library'}, [icon(`${prefix}-library-icon`, 'library', 24, {color: colors.muted}), text(`${prefix}-library-heading-label`, 'Your library', 16, {fontWeight: 700})])] : []
      for (const [page, label] of [['home', 'Home'], ['search', 'Search'], ['library', 'Your library']]) {
        if (!wide) children.push(node(`${prefix}-${page}`, 'Column', {weight: 1, spacing: 4, horizontalAlignment: 'center', action: `page-${page}`}, [icon(`${prefix}-${page}-icon`, page, 26, {color: state.page === page ? colors.text : colors.muted}), text(`${prefix}-${page}-label`, label, 10, {color: state.page === page ? colors.text : colors.muted, maxLines: 1})]))
      }
      if (wide) {
        children.push(node(`${prefix}-sidebar-library`, 'LazyColumn', {weight: 1, fillWidth: true, spacing: 12}, state.sidebarTracks.map((track, index) => node(`${prefix}-sidebar-${index}`, 'Row', {fillWidth: true, spacing: 8}, [node(`${prefix}-sidebar-art-${index}`, 'Artwork', {url: track.artwork || '', description: track.title, width: 48, height: 48, radius: 4}), node(`${prefix}-sidebar-meta-${index}`, 'Column', {weight: 1, spacing: 2}, [button(`${prefix}-sidebar-play-${index}`, track.title, action(() => play([track])), {maxLines: 1}), text(`${prefix}-sidebar-artist-${index}`, track.artist, 12, {color: colors.muted, maxLines: 1})])]))))
      }
      return node(`${prefix}-navigation`, wide ? 'Column' : 'Row', {spacing: wide ? 12 : 4, padding: wide ? 16 : 4, background: wide ? colors.panel : colors.base, radius: wide ? 8 : 0, ...(wide ? {width: 248, fillHeight: true} : {height: 60, fillWidth: true})}, children)
    }
    function content(prefix, wide) {
      const children = [node(`${prefix}-heading`, 'Row', {spacing: 12, fillWidth: true}, [
        ...(!wide ? [button(`${prefix}-profile`, state.source.accountName?.slice(0, 1).toUpperCase() || 'M', 'settings', {background: colors.accent, color: colors.base, radius: 24, width: 36, height: 36})] : []),
        ...(state.page === 'home' ? [node(`${prefix}-filters`, 'LazyRow', {spacing: 8, height: 40, weight: 1}, [['all', 'All'], ['track', 'Music'], ['playlist', 'Playlists'], ['album', 'Albums'], ['artist', 'Artists']].map(([kind, label]) => button(`${prefix}-filter-${kind}`, label, `filter-${kind}`, {background: state.filter === kind ? colors.accent : colors.raised, color: state.filter === kind ? colors.base : colors.text, radius: 20, height: 36})))] : [text(`${prefix}-title`, title(), 26, {weight: 1})]),
        ...(!wide ? [node(`${prefix}-recovery-clearance`, 'Spacer', {width: 40})] : [])
      ])]
      if (state.page === 'collection' && state.collection) {
        const item = state.collection.item
        children.push(iconButton(`${prefix}-collection-back`, 'back', 'Back', 'page-home'))
        children.push(node(`${prefix}-collection-art`, 'Artwork', {url: item.artwork || '', description: item.title || item.name, width: 220, height: 220, radius: 4}))
        children.push(text(`${prefix}-collection-title`, item.title || item.name, 30))
        if (item.artist || item.author) children.push(text(`${prefix}-collection-author`, item.artist || item.author, 14, {color: colors.muted}))
      }
      if (state.page === 'search' && !wide) children.push(node(`${prefix}-query`, 'TextField', {label: 'What do you want to listen to?', appearance: 'search', action: 'search', value: state.query, fillWidth: true, background: colors.raised, radius: 28}))
      if (state.loading) children.push(text(`${prefix}-loading`, 'Loading…', 14, {color: colors.muted}))
      if (state.message) children.push(text(`${prefix}-message`, state.message, 14, {color: colors.muted}), button(`${prefix}-retry`, 'Retry', 'refresh', {color: colors.accent}))
      if (state.page === 'home') {
        const shelves = state.shelves.map(shelf => ({...shelf, items: shelf.items.filter(entry => state.filter === 'all' || entry[state.filter])})).filter(shelf => shelf.items.length)
        const quick = shelves.flatMap(shelf => shelf.items).filter((entry, index, entries) => entries.findIndex(value => (value.track || value.album || value.artist || value.playlist)?.id === (entry.track || entry.album || entry.artist || entry.playlist)?.id) === index).slice(0, 8)
        const columns = wide ? 4 : 2
        for (let index = 0; index < quick.length; index += columns) children.push(node(`${prefix}-quick-${index}`, 'Row', {spacing: 8, fillWidth: true}, quick.slice(index, index + columns).map((entry, offset) => {
          const kind = ['track', 'album', 'artist', 'playlist'].find(key => entry[key]); const item = entry[kind]; const id = `${prefix}-quick-${index}-${offset}`
          return node(id, 'Row', {weight: 1, background: colors.raised, radius: 4, spacing: 8, height: 56, action: action(() => kind === 'track' ? play([item]) : collection(kind, item))}, [node(`${id}-art`, 'Artwork', {url: item.artwork || '', width: 56, height: 56, description: item.title || item.name}), text(`${id}-open`, item.title || item.name, 12, {weight: 1, maxLines: 2, fontWeight: 700})])
        })))
        shelves.slice(0, 6).forEach((value, index) => children.push(shelf(prefix, {...value, items: value.items.slice(0, 12)}, index, wide)))
        if (!state.shelves.length && !state.loading) children.push(text(`${prefix}-home-empty`, 'Choose a source in the original Home interface to see its recommendations. Your saved music is in Library.', 16, {color: colors.muted}))
      } else {
        if (state.page === 'search') for (const [kind, key] of [['album', 'albums'], ['artist', 'artists'], ['playlist', 'playlists']]) {
          for (const [index, item] of (state.catalogResults[key] || []).slice(0, 6).entries()) {
            const id = `${prefix}-result-${kind}-${index}`
            children.push(node(id, 'Row', {fillWidth: true, spacing: 12}, [node(`${id}-art`, 'Artwork', {url: item.artwork || '', description: item.title || item.name, width: 52, height: 52, radius: kind === 'artist' ? 26 : 4}), node(`${id}-meta`, 'Column', {weight: 1}, [button(`${id}-open`, item.title || item.name, action(() => collection(kind, item)), {maxLines: 1}), text(`${id}-detail`, item.artist || item.author || '', 12, {color: colors.muted})])]))
          }
        }
        const tracks = state.page === 'queue' ? state.player.queue : state.page === 'library' ? state.tracks : state.results
        if (tracks.length) {
          if (state.page !== 'queue') children.push(button(`${prefix}-play-all`, '▶  Play all', action(() => play(tracks)), {color: colors.accent}))
          tracks.slice(0, 80).forEach((track, index) => children.push(trackRow(prefix, track, tracks, index)))
          if (tracks.length > 80) children.push(text(`${prefix}-limit`, 'Showing the first 80 tracks. Play all includes the full loaded list.', 12, {color: colors.muted}))
        } else if (!state.loading) children.push(text(`${prefix}-empty`, state.page === 'search' ? 'Search your local library and active catalog.' : 'No tracks here yet. Add local music or select a provider in the original interface.', 16, {color: colors.muted}))
        if (state.page === 'library') children.push(node(`${prefix}-pages`, 'Row', {spacing: 8}, [
          button(`${prefix}-back`, 'Previous page', 'back', {enabled: !state.loading && state.offset > 0}),
          button(`${prefix}-more`, 'Next page', 'more', {enabled: !state.loading && state.more})
        ]))
      }
      return node(`${prefix}-content`, 'LazyColumn', {fillSize: true, padding: wide ? 24 : 16, spacing: 16, background: colors.panel, radius: wide ? 8 : 0, weight: 1}, children)
    }
    function player(prefix, wide) {
      const value = state.player, track = value.track, duration = Number(track.duration || 0), expanded = state.expanded
      if (!track.id) return node(`${prefix}-player-empty`, 'Spacer', {height: 1})
      const seek = node(`${prefix}-seek-row`, 'Row', {fillWidth: true, spacing: 8}, [text(`${prefix}-elapsed`, time(value.position), 11, {color: colors.muted}), node(`${prefix}-seek`, 'Slider', {weight: 1, value: duration > 0 ? Math.min(1, value.position / duration) : 0, enabled: duration > 0 && capabilities.has('seek'), action: 'seek'}), text(`${prefix}-total`, time(duration), 11, {color: colors.muted})])
      const toggle = iconButton(`${prefix}-toggle`, value.playing ? 'pause' : 'play', value.playing ? 'Pause' : 'Play', 'toggle', {background: expanded || wide ? colors.text : '#00000000', color: expanded || wide ? colors.base : colors.text, radius: 14, width: expanded ? 64 : 40, height: expanded ? 64 : 40, iconSize: expanded ? 32 : 22, enabled: capabilities.has('toggle')})
      const controls = node(`${prefix}-transport`, 'Row', {fillWidth: true, horizontalArrangement: 'center', spacing: expanded ? 20 : 12}, [iconButton(`${prefix}-shuffle`, 'shuffle', 'Shuffle', 'shuffle', {enabled: capabilities.has('shuffle'), color: colors.muted, iconSize: 18}), iconButton(`${prefix}-previous`, 'previous', 'Previous', 'previous', {enabled: capabilities.has('previous'), color: expanded ? colors.text : colors.muted, iconSize: expanded ? 28 : 20}), toggle, iconButton(`${prefix}-next`, 'next', 'Next', 'next', {enabled: capabilities.has('next'), color: expanded ? colors.text : colors.muted, iconSize: expanded ? 28 : 20}), iconButton(`${prefix}-repeat`, 'repeat', 'Repeat', 'repeat', {enabled: capabilities.has('repeat'), color: colors.muted, iconSize: 18})])
      const metadata = node(`${prefix}-playing-meta`, 'Column', {weight: 1, spacing: 4, action: 'open-player'}, [text(`${prefix}-playing-title`, track.title || 'Nothing playing', expanded ? 24 : 14, {maxLines: 1, fontWeight: expanded ? 700 : 600}), text(`${prefix}-playing-artist`, track.artist || '', expanded ? 16 : 12, {color: colors.muted, maxLines: 1})])
      if (wide && !expanded) return node(`${prefix}-player`, 'Row', {fillWidth: true, height: 96, padding: 12, spacing: 24, background: colors.base}, [
        node(`${prefix}-desktop-track`, 'Row', {weight: 1, spacing: 12, action: 'open-player'}, [node(`${prefix}-playing-art`, 'Artwork', {url: track.artwork || '', description: track.title, width: 56, height: 56, radius: 4}), metadata]),
        node(`${prefix}-desktop-center`, 'Column', {weight: 1.5, spacing: 0}, [controls, seek]),
        node(`${prefix}-desktop-options`, 'Row', {weight: 1, spacing: 4, horizontalArrangement: 'end'}, [iconButton(`${prefix}-queue`, 'queue', 'Queue', 'player-queue', {color: colors.muted, iconSize: 20}), iconButton(`${prefix}-expand`, 'expand', 'Now playing', 'open-player', {color: colors.muted, iconSize: 20})])
      ])
      if (!expanded) return node(`${prefix}-player-wrap`, 'Column', {fillWidth: true, padding: 8, spacing: 0}, [node(`${prefix}-player`, 'Column', {fillWidth: true, background: '#304866', radius: 6, padding: 8, spacing: 4}, [node(`${prefix}-player-top`, 'Row', {fillWidth: true, spacing: 12}, [node(`${prefix}-playing-art`, 'Artwork', {url: track.artwork || '', description: track.title, width: 40, height: 40, radius: 4, action: 'open-player'}), metadata, toggle]), node(`${prefix}-progress`, 'Progress', {fillWidth: true, height: 2, value: duration > 0 ? Math.min(1, value.position / duration) : 0, color: colors.text, trackColor: '#33FFFFFF'})])])
      return node(`${prefix}-player`, 'LazyColumn', {fillWidth: true, weight: 1, padding: wide ? 48 : 24, spacing: 20, horizontalAlignment: 'center', background: '#202F47'}, [
        node(`${prefix}-player-heading`, 'Row', {fillWidth: true, spacing: 8}, [iconButton(`${prefix}-close`, 'down', 'Close player', 'close-player'), text(`${prefix}-now-playing`, 'Now playing', 12, {weight: 1, fontWeight: 600}), iconButton(`${prefix}-queue`, 'queue', 'Queue', 'player-queue')]),
        node(`${prefix}-large-art`, 'Artwork', {url: track.artwork || '', description: track.title, ...(wide ? {width: 400, height: 400} : {fillWidth: true, aspectRatio: 1}), radius: 6}),
        node(`${prefix}-player-meta-row`, 'Row', {fillWidth: true}, [metadata]),
        seek, controls,
        text(`${prefix}-album`, track.album || '', 13, {color: colors.muted, fillWidth: true})
      ])
    }
    function render() {
      if (stopped) return
      // Action IDs are bounded to the current tree and reused by the host on refresh.
      serial = 0
      const wide = node('wide-shell', 'Column', {fillSize: true, background: colors.base}, [
        ...(state.expanded ? [] : [node('wide-global-header', 'Row', {fillWidth: true, height: 64, spacing: 12, padding: 8}, [iconButton('wide-brand', 'library', 'MetroFlow', 'page-home', {iconSize: 28}), text('wide-wordmark', 'MetroFlow', 18, {fontWeight: 700}), iconButton('wide-global-home', 'home', 'Home', 'page-home', {background: colors.raised, radius: 24, width: 48, height: 48}), node('wide-global-search', 'TextField', {appearance: 'search', label: 'Search your music', action: 'search', value: state.query, width: 340, height: 48, background: colors.raised, radius: 12}), node('wide-global-right-space', 'Spacer', {weight: 1}), iconButton('wide-global-settings', 'settings', 'Settings', 'settings', {color: colors.muted, iconSize: 20}), button('wide-global-profile', state.source.accountName?.slice(0, 1).toUpperCase() || 'M', 'settings', {background: colors.accent, color: colors.base, radius: 24, width: 36, height: 36}), node('wide-recovery-clearance', 'Spacer', {width: 40})])]),
        ...(state.expanded ? [] : [node('wide-main', 'Row', {weight: 1, fillWidth: true, spacing: 8, padding: 8}, [navigation('wide', true), content('wide', true)])]), player('wide', true)
      ])
      const compact = node('compact-shell', 'Column', {fillSize: true, background: colors.base}, [...(state.expanded ? [] : [content('compact', false)]), player('compact', false), ...(state.expanded ? [] : [navigation('compact', false)])])
      host.ui('ui.app', node('metroflow', 'Adaptive', {fillSize: true, breakpoint: 840}, [wide, compact]))
    }
    async function loadLibrary(reset = false, direction = 0) {
      if (reset) { libraryEpoch++; state.tracks = []; state.offset = 0; state.more = true }
      const epoch = libraryEpoch
      if (!reset && state.loading) return
      state.loading = true; state.message = ''; render()
      try {
        const offset = Math.max(0, state.offset + direction * 50)
        const tracks = await host.library(offset, 50)
        if (stopped || epoch !== libraryEpoch) return
        state.offset = offset; state.more = tracks.length === 50
        state.tracks = tracks
      } catch (_) { if (epoch === libraryEpoch) state.message = 'Could not load your library. Press Refresh to retry.' }
      if (stopped || epoch !== libraryEpoch) return
      state.loading = false; render()
    }
    async function loadHome() {
      const ticket = ++request
      state.loading = true; state.message = ''; render()
      try {
        const source = await host.call('ui.source/v1')
        if (stopped || ticket !== request) return
        state.source = source
        const response = await host.call('ui.home/v1')
        if (!stopped && ticket === request) state.shelves = response.shelves || []
      }
      catch (_) { if (ticket === request) state.message = 'Could not load Home. Press Refresh to retry.' }
      if (!stopped && ticket === request) { state.loading = false; render() }
    }
    async function search(query) {
      const ticket = ++request
      state.page = 'search'; state.catalogResults = {}; state.query = query.trim(); state.loading = true; state.results = []; state.message = ''; render()
      try {
        const response = await host.call('ui.search/v1', {query: state.query, limit: 60})
        if (!stopped && ticket === request) { state.results = response.results?.tracks || []; state.catalogResults = response.results || {} }
      } catch (_) { if (ticket === request) state.message = 'Search failed. Check your source and try again.' }
      if (!stopped && ticket === request) { state.loading = false; render() }
    }
    for (const page of ['home', 'search', 'library', 'queue']) host.action(`page-${page}`, () => {
      request++; libraryEpoch++; state.loading = false; state.page = page; state.message = ''
      if (page === 'library') return loadLibrary(true)
      if (page === 'home') return loadHome()
      if (page === 'search') { state.results = []; state.catalogResults = {} }
      render()
    })
    for (const kind of ['all', 'track', 'album', 'artist', 'playlist']) host.action(`filter-${kind}`, () => { state.filter = kind; render() })
    host.action('open-player', () => { state.expanded = true; render() })
    host.action('close-player', () => { state.expanded = false; render() })
    host.action('player-queue', () => { state.expanded = false; state.page = 'queue'; render() })
    host.action('plugins', () => host.navigate('plugins').catch(report))
    host.action('settings', () => host.navigate('settings').catch(report))
    host.action('search', search)
    host.action('more', () => loadLibrary(false, 1))
    host.action('back', () => loadLibrary(false, -1))
    host.action('refresh', () => state.page === 'home' ? loadHome() : state.page === 'library' ? loadLibrary(true) : state.page === 'search' ? search(state.query) : state.page === 'collection' ? collection(state.collection.kind, state.collection.item) : render())
    for (const command of ['previous', 'toggle', 'next', 'shuffle', 'repeat']) host.action(command, () => host.player(command).catch(report))
    host.action('seek', value => { const fraction = Number(value); if (Number.isFinite(fraction)) return host.player('seek', Math.round(Math.max(0, Math.min(1, fraction)) * state.player.track.duration)).catch(report) })
    subscriptions.push(host.on('playback_changed', value => {
      const old = state.player; state.player = value
      if (JSON.stringify(old.track) !== JSON.stringify(value.track) || (state.page === 'queue' && JSON.stringify(old.queue) !== JSON.stringify(value.queue))) { render(); return }
      const changes = {}
      for (const prefix of ['wide', 'compact']) {
        changes[`${prefix}-toggle`] = {icon: value.playing ? 'pause' : 'play', description: value.playing ? 'Pause' : 'Play'}
        if (prefix === 'wide' || state.expanded) {
          changes[`${prefix}-elapsed`] = {text: time(value.position)}
          changes[`${prefix}-seek`] = {value: String(value.track.duration > 0 ? Math.min(1, value.position / value.track.duration) : 0)}
        } else changes[`${prefix}-progress`] = {value: String(value.track.duration > 0 ? Math.min(1, value.position / value.track.duration) : 0)}
      }
      host.patchMany(changes)
    }))
    subscriptions.push(host.on('ui_home_changed', () => { if (state.page === 'home') return loadHome() }))
    subscriptions.push(host.on('library_changed', () => { if (state.page === 'library') return loadLibrary(true) }))
    globalThis.plugin.dispose = () => { stopped = true; request++; libraryEpoch++; subscriptions.forEach(unsubscribe => unsubscribe()) }
    render()
    // Register the usable shell before a provider or library request completes.
    void loadHome()
    void host.library(0, 24).then(tracks => { if (!stopped) { state.sidebarTracks = tracks; render() } }).catch(() => {})
  }
}
