// MetroFlow uses the active MetroFuse catalog and player; it supplies no provider.
globalThis.plugin = {
  init(host) {
    const colors = {
      base: '#000000',
      panel: '#121212',
      elevated: '#1F1F1F',
      raised: '#282828',
      highlight: '#333333',
      text: '#FFFFFF',
      muted: '#B3B3B3',
      accent: '#1ED760',
    };
    const genres = [
      ['Pop', 'Pop hits', '#148A08'],
      ['Hip-Hop', 'Hip Hop', '#BC5900'],
      ['Electronic', 'Electronic dance', '#006450'],
      ['Rock', 'Rock', '#E91429'],
      ['R&B', 'R&B soul', '#DC148C'],
      ['Chill', 'Chill lofi', '#477D95'],
    ];
    const state = {
      page: 'home',
      tracks: [],
      sidebarTracks: [],
      shelves: [],
      results: [],
      query: '',
      offset: 0,
      more: true,
      loading: false,
      message: '',
      player: host.playerState(),
      expanded: false,
      nowPlayingPanel: true,
      filter: 'all',
      libraryFilter: 'songs',
      source: {},
      frontends: {selected: '', sources: []},
      showFrontends: false,
      catalogResults: {},
      adminSettings: null,
      adminPlugins: null,
      pluginTab: 'installed',
    };
    const capabilities = new Set(host.playerCapabilities());
    let serial = 0;
    let request = 0;
    let libraryEpoch = 0;
    let stopped = false;
    const subscriptions = [];
    const node = (id, type, props = {}, children = []) => ({
      id,
      type,
      props: Object.fromEntries(Object.entries(props).map(([k, v]) => [k, String(v)])),
      children,
    });
    const text = (id, value, size = 16, props = {}) =>
      node(id, 'Text', {text: value, color: colors.text, fontSize: size, fontWeight: size >= 20 ? 700 : 400, maxLines: 2, ...props});
    const button = (id, value, action, props = {}) =>
      node(id, 'TextButton', {text: value, action, color: colors.text, fontSize: 14, fontWeight: 600, ...props});
    const icon = (id, name, size = 24, props = {}) => node(id, 'Icon', {icon: name, iconSize: size, color: colors.text, ...props});
    const iconButton = (id, name, label, action, props = {}) =>
      node(id, 'IconButton', {icon: name, description: label, action, color: colors.text, width: 40, height: 40, ...props});
    const time = value => {
      const seconds = Math.max(0, Math.floor(Number(value || 0) / 1000));
      return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
    };
    const title = () => ({
      home: 'Home',
      library: 'Your library',
      search: 'Search',
      queue: 'Up next',
      collection: state.collectionTitle || 'Collection',
    })[state.page];
    const action = fn => {
      const id = `item-${serial++}`;
      host.action(id, fn);
      return id;
    };
    const report = () => {
      state.message = 'Could not load this content. Check your selected source, then retry.';
      render();
    };
    const play = tracks => host.playTracks(tracks).catch(report);

    function trackRow(prefix, track, tracks, index) {
      const current = track.id === state.player.track.id;
      return node(`${prefix}-row-${index}`, 'Row', {fillWidth: true, background: current ? colors.elevated : colors.panel, radius: 6, padding: 8, spacing: 10}, [
        node(`${prefix}-art-${index}`, 'Artwork', {url: track.artwork || '', description: track.title, width: 48, height: 48, radius: 4}),
        node(`${prefix}-meta-${index}`, 'Column', {weight: 1, spacing: 2}, [
          button(
            `${prefix}-play-${index}`,
            track.title,
            action(() => (state.page === 'queue' ? host.player('queueIndex', index) : host.playTracks(tracks, index)).catch(report)),
            {enabled: capabilities.has(state.page === 'queue' ? 'queueIndex' : 'playTracks'), color: current ? colors.accent : colors.text, maxLines: 1, fillWidth: true},
          ),
          text(`${prefix}-artist-${index}`, track.artist, 12, {color: colors.muted, maxLines: 1}),
        ]),
        text(`${prefix}-duration-${index}`, time(track.duration), 12, {color: colors.muted}),
        iconButton(`${prefix}-add-${index}`, 'add', 'Add to queue', action(() => host.addToQueue([track]).catch(report)), {
          enabled: capabilities.has('addToQueue'),
          iconSize: 18,
          color: colors.muted,
        }),
      ]);
    }

    async function collection(kind, item) {
      const ticket = ++request;
      state.collection = {kind, item};
      state.page = 'collection';
      state.collectionTitle = item.title || item.name;
      state.results = [];
      state.loading = true;
      state.message = '';
      render();
      try {
        const response = await host.call('ui.collection/v1', {kind, id: item.id});
        if (stopped || ticket !== request) return;
        state.results = response.item?.tracks || [];
        state.collection.item = response.item || item;
        if (!response.item) state.message = 'This collection is unavailable from the active source.';
      } catch (_) {
        if (ticket === request) state.message = 'Could not open this collection. Try again from Home.';
      }
      if (stopped || ticket !== request) return;
      state.loading = false;
      render();
    }

    function shelf(prefix, shelfItem, shelfIndex, wide) {
      const size = wide ? 168 : 148;
      return node(`${prefix}-shelf-${shelfIndex}`, 'Column', {spacing: 10}, [
        text(`${prefix}-shelf-title-${shelfIndex}`, shelfItem.title, 20, {fontWeight: 700}),
        node(
          `${prefix}-cards-${shelfIndex}`,
          'LazyRow',
          {height: size + 70, spacing: 14},
          shelfItem.items.map((entry, index) => {
            const kind = ['track', 'album', 'artist', 'playlist'].find(key => entry[key]);
            if (!kind) return node(`${prefix}-unknown-${shelfIndex}-${index}`, 'Spacer');
            const item = entry[kind];
            const id = `${prefix}-card-${shelfIndex}-${index}`;
            return node(id, 'Column', {width: size, spacing: 8, action: action(() => (kind === 'track' ? play([item]) : collection(kind, item)))}, [
              node(`${id}-art`, 'Artwork', {url: item.artwork || '', description: item.title || item.name, width: size, height: size, radius: kind === 'artist' ? 84 : 6}),
              text(`${id}-open`, item.title || item.name, 14, {maxLines: 1, fontWeight: 600, fillWidth: true}),
              text(`${id}-subtitle`, item.artist || item.author || '', 12, {color: colors.muted, maxLines: 1}),
            ]);
          }),
        ),
      ]);
    }

    function navigation(prefix, wide) {
      const children = wide
        ? [
            node(`${prefix}-library-heading`, 'Row', {fillWidth: true, spacing: 10, action: 'page-library'}, [
              icon(`${prefix}-library-icon`, 'library', 22, {color: colors.muted}),
              text(`${prefix}-library-heading-label`, 'Your Library', 15, {fontWeight: 700, weight: 1}),
            ]),
          ]
        : [];
      for (const [page, label] of [['home', 'Home'], ['search', 'Search'], ['library', 'Your library']]) {
        if (!wide) {
          children.push(
            node(`${prefix}-${page}`, 'Column', {weight: 1, spacing: 4, horizontalAlignment: 'center', action: `page-${page}`}, [
              icon(`${prefix}-${page}-icon`, page, 24, {color: state.page === page ? colors.text : colors.muted}),
              text(`${prefix}-${page}-label`, label, 11, {color: state.page === page ? colors.text : colors.muted, maxLines: 1}),
            ]),
          );
        }
      }
      if (wide) {
        children.push(
          node(
            `${prefix}-sidebar-library`,
            'LazyColumn',
            {weight: 1, fillWidth: true, spacing: 10},
            state.sidebarTracks.map((track, index) =>
              node(`${prefix}-sidebar-${index}`, 'Row', {fillWidth: true, spacing: 10}, [
                node(`${prefix}-sidebar-art-${index}`, 'Artwork', {url: track.artwork || '', description: track.title, width: 46, height: 46, radius: 4}),
                node(`${prefix}-sidebar-meta-${index}`, 'Column', {weight: 1, spacing: 2}, [
                  button(`${prefix}-sidebar-play-${index}`, track.title, action(() => play([track])), {maxLines: 1}),
                  text(`${prefix}-sidebar-artist-${index}`, track.artist, 12, {color: colors.muted, maxLines: 1}),
                ]),
              ]),
            ),
          ),
        );
      }
      return node(
        `${prefix}-navigation`,
        wide ? 'Column' : 'Row',
        {spacing: wide ? 12 : 4, padding: wide ? 14 : 4, background: wide ? colors.panel : colors.base, radius: wide ? 8 : 0, ...(wide ? {width: 260, fillHeight: true} : {height: 60, fillWidth: true})},
        children,
      );
    }

    function nowPlayingSidebar() {
      const track = state.player.track;
      if (!track?.id || !state.nowPlayingPanel) return null;
      const idx = Number.isInteger(state.player.currentIndex) ? state.player.currentIndex : state.player.queue.findIndex(t => t.id === track.id);
      const nextTrack = idx >= 0 ? state.player.queue[idx + 1] : null;
      return node('wide-now-playing-panel', 'Column', {width: 284, fillHeight: true, padding: 14, spacing: 14, background: colors.panel, radius: 8}, [
        node('wide-np-head', 'Row', {fillWidth: true}, [
          text('wide-np-label', 'Now playing', 14, {fontWeight: 700, weight: 1}),
          iconButton('wide-np-close', 'close', 'Close panel', 'toggle-np-panel', {color: colors.muted, iconSize: 18, width: 32, height: 32}),
        ]),
        node('wide-np-art', 'Artwork', {url: track.artwork || '', description: track.title, width: 256, height: 256, radius: 8, action: 'open-player'}),
        text('wide-np-title', track.title, 18, {fontWeight: 700, maxLines: 2}),
        text('wide-np-artist', track.artist || '', 13, {color: colors.muted, maxLines: 1}),
        ...(nextTrack
          ? [
              node('wide-np-next', 'Column', {fillWidth: true, padding: 12, spacing: 8, background: colors.elevated, radius: 8, action: 'page-queue'}, [
                text('wide-np-next-title', 'Next in queue', 12, {fontWeight: 700, color: colors.muted}),
                text('wide-np-next-song', nextTrack.title, 13, {fontWeight: 600, maxLines: 1}),
                text('wide-np-next-artist', nextTrack.artist || '', 12, {color: colors.muted, maxLines: 1}),
              ]),
            ]
          : []),
      ]);
    }

    function content(prefix, wide) {
      const children = [
        node(`${prefix}-heading`, 'Row', {spacing: 10, fillWidth: true}, [
          ...(!wide
            ? [
                button(`${prefix}-profile`, state.source.accountName?.slice(0, 1).toUpperCase() || 'M', 'settings', {
                  background: colors.accent,
                  color: colors.base,
                  radius: 24,
                  width: 34,
                  height: 34,
                }),
              ]
            : []),
          ...(state.page === 'home'
            ? [
                node(
                  `${prefix}-filters`,
                  'LazyRow',
                  {spacing: 8, height: 40, weight: 1},
                  [['all', 'All'], ['track', 'Music'], ['playlist', 'Playlists'], ['album', 'Albums'], ['artist', 'Artists']].map(([kind, label]) =>
                    button(`${prefix}-filter-${kind}`, label, `filter-${kind}`, {
                      background: state.filter === kind ? colors.accent : colors.raised,
                      color: state.filter === kind ? colors.base : colors.text,
                      radius: 20,
                      height: 36,
                    }),
                  ),
                ),
                ...(!wide
                  ? [
                      button(`${prefix}-source-btn`, state.source.title || 'Offline', 'open-sources', {
                        background: colors.raised,
                        color: colors.text,
                        radius: 20,
                        height: 34,
                      }),
                    ]
                  : []),
              ]
            : [text(`${prefix}-title`, title(), 24, {weight: 1, fontWeight: 700})]),
          ...(!wide ? [node(`${prefix}-recovery-clearance`, 'Spacer', {width: 40})] : []),
        ]),
      ];

      if (state.showFrontends && state.frontends.sources.length) {
        children.push(
          node(`${prefix}-sources-box`, 'Column', {fillWidth: true, padding: 12, spacing: 6, background: colors.elevated, radius: 10}, [
            text(`${prefix}-sources-title`, 'Select music source', 14, {fontWeight: 700}),
            ...[{id: '', title: 'Offline'}, ...state.frontends.sources].map((src, idx) =>
              button(`${prefix}-src-${idx}`, (state.frontends.selected === src.id ? '✓ ' : '') + src.title, action(() => selectFrontend(src.id)), {
                color: state.frontends.selected === src.id ? colors.accent : colors.text,
              }),
            ),
            button(`${prefix}-src-close`, 'Close', 'close-sources', {color: colors.muted}),
          ]),
        );
      }

      if (state.page === 'collection' && state.collection) {
        const item = state.collection.item;
        children.push(iconButton(`${prefix}-collection-back`, 'back', 'Back', 'page-home'));
        children.push(node(`${prefix}-collection-art`, 'Artwork', {url: item.artwork || '', description: item.title || item.name, width: 200, height: 200, radius: 6}));
        children.push(text(`${prefix}-collection-title`, item.title || item.name, 28, {fontWeight: 700}));
        if (item.artist || item.author) children.push(text(`${prefix}-collection-author`, item.artist || item.author, 14, {color: colors.muted}));
      }
      if (state.page === 'search' && !wide) {
        children.push(node(`${prefix}-query`, 'TextField', {label: 'What do you want to play?', appearance: 'search', action: 'search', value: state.query, fillWidth: true, background: colors.elevated, radius: 28}));
      }
      if (state.page === 'search' && !state.query) {
        children.push(text(`${prefix}-browse-title`, 'Browse all', 18, {fontWeight: 700}));
        const cols = wide ? 3 : 2;
        for (let i = 0; i < genres.length; i += cols) {
          children.push(
            node(
              `${prefix}-genre-row-${i}`,
              'Row',
              {spacing: 10, fillWidth: true},
              genres.slice(i, i + cols).map(([label, q, bg], j) =>
                node(`${prefix}-genre-${i}-${j}`, 'Box', {weight: 1, height: 84, radius: 8, padding: 12, background: bg, action: action(() => search(q))}, [
                  text(`${prefix}-genre-label-${i}-${j}`, label, 16, {fontWeight: 700}),
                ]),
              ),
            ),
          );
        }
      }
      if (state.loading) children.push(text(`${prefix}-loading`, 'Loading…', 14, {color: colors.muted}));
      if (state.message) children.push(text(`${prefix}-message`, state.message, 14, {color: colors.muted}), button(`${prefix}-retry`, 'Retry', 'refresh', {color: colors.accent}));
      if (state.page === 'home') {
        const shelves = state.shelves
          .map(s => ({...s, items: s.items.filter(entry => state.filter === 'all' || entry[state.filter])}))
          .filter(s => s.items.length);
        const quick = shelves
          .flatMap(s => s.items)
          .filter((entry, index, entries) => entries.findIndex(v => (v.track || v.album || v.artist || v.playlist)?.id === (entry.track || entry.album || entry.artist || entry.playlist)?.id) === index)
          .slice(0, 8);
        const columns = wide ? 4 : 2;
        for (let index = 0; index < quick.length; index += columns) {
          children.push(
            node(
              `${prefix}-quick-${index}`,
              'Row',
              {spacing: 8, fillWidth: true},
              quick.slice(index, index + columns).map((entry, offset) => {
                const kind = ['track', 'album', 'artist', 'playlist'].find(key => entry[key]);
                const item = entry[kind];
                const id = `${prefix}-quick-${index}-${offset}`;
                return node(id, 'Row', {weight: 1, background: colors.raised, radius: 6, spacing: 8, height: 56, action: action(() => (kind === 'track' ? play([item]) : collection(kind, item)))}, [
                  node(`${id}-art`, 'Artwork', {url: item.artwork || '', width: 56, height: 56, description: item.title || item.name}),
                  text(`${id}-open`, item.title || item.name, 12, {weight: 1, maxLines: 2, fontWeight: 700}),
                ]);
              }),
            ),
          );
        }
        shelves.slice(0, 6).forEach((value, index) => children.push(shelf(prefix, {...value, items: value.items.slice(0, 12)}, index, wide)));
        if (!state.shelves.length && !state.loading) {
          children.push(text(`${prefix}-home-empty`, 'Choose a music source to see recommendations. Your saved music is in Library.', 15, {color: colors.muted}));
        }
      } else {
        if (state.page === 'search' && state.query) {
          for (const [kind, key] of [['album', 'albums'], ['artist', 'artists'], ['playlist', 'playlists']]) {
            for (const [index, item] of (state.catalogResults[key] || []).slice(0, 6).entries()) {
              const id = `${prefix}-result-${kind}-${index}`;
              children.push(
                node(id, 'Row', {fillWidth: true, spacing: 12}, [
                  node(`${id}-art`, 'Artwork', {url: item.artwork || '', description: item.title || item.name, width: 52, height: 52, radius: kind === 'artist' ? 26 : 4}),
                  node(`${id}-meta`, 'Column', {weight: 1}, [
                    button(`${id}-open`, item.title || item.name, action(() => collection(kind, item)), {maxLines: 1}),
                    text(`${id}-detail`, item.artist || item.author || '', 12, {color: colors.muted}),
                  ]),
                ]),
              );
            }
          }
        }
        const tracks = state.page === 'queue' ? state.player.queue : state.page === 'library' ? state.tracks : state.results;
        if (tracks.length) {
          if (state.page !== 'queue') children.push(button(`${prefix}-play-all`, '▶  Play all', action(() => play(tracks)), {color: colors.accent}));
          tracks.slice(0, 80).forEach((track, index) => children.push(trackRow(prefix, track, tracks, index)));
        } else if (!state.loading && !(state.page === 'search' && !state.query)) {
          children.push(text(`${prefix}-empty`, state.page === 'search' ? 'No results found.' : 'No tracks here yet.', 15, {color: colors.muted}));
        }
        if (state.page === 'library') {
          children.push(
            node(`${prefix}-pages`, 'Row', {spacing: 8}, [
              button(`${prefix}-back`, 'Previous page', 'back', {enabled: !state.loading && state.offset > 0, color: colors.accent}),
              button(`${prefix}-more`, 'Next page', 'more', {enabled: !state.loading && state.more, color: colors.accent}),
            ]),
          );
        }
      }
      return node(`${prefix}-content`, 'LazyColumn', {fillSize: true, padding: wide ? 24 : 16, spacing: 16, background: colors.panel, radius: wide ? 8 : 0, weight: 1}, children);
    }

    function player(prefix, wide) {
      const value = state.player;
      const track = value.track;
      const duration = Number(track.duration || 0);
      const expanded = state.expanded;
      if (!track.id) return node(`${prefix}-player-empty`, 'Spacer', {height: 1});
      const seek = node(`${prefix}-seek-row`, 'Row', {fillWidth: true, spacing: 8}, [
        text(`${prefix}-elapsed`, time(value.position), 11, {color: colors.muted}),
        node(`${prefix}-seek`, 'Slider', {weight: 1, value: duration > 0 ? Math.min(1, value.position / duration) : 0, enabled: duration > 0 && capabilities.has('seek'), action: 'seek'}),
        text(`${prefix}-total`, time(duration), 11, {color: colors.muted}),
      ]);
      const toggle = iconButton(`${prefix}-toggle`, value.playing ? 'pause' : 'play', value.playing ? 'Pause' : 'Play', 'toggle', {
        background: expanded || wide ? colors.text : '#00000000',
        color: expanded || wide ? colors.base : colors.text,
        radius: 20,
        width: expanded ? 64 : 36,
        height: expanded ? 64 : 36,
        iconSize: expanded ? 32 : 22,
        enabled: capabilities.has('toggle'),
      });
      const controls = node(`${prefix}-transport`, 'Row', {fillWidth: true, horizontalArrangement: 'center', spacing: expanded ? 20 : 12}, [
        iconButton(`${prefix}-shuffle`, 'shuffle', 'Shuffle', 'shuffle', {enabled: capabilities.has('shuffle'), color: colors.muted, iconSize: 18}),
        iconButton(`${prefix}-previous`, 'previous', 'Previous', 'previous', {enabled: capabilities.has('previous'), color: expanded ? colors.text : colors.muted, iconSize: expanded ? 28 : 20}),
        toggle,
        iconButton(`${prefix}-next`, 'next', 'Next', 'next', {enabled: capabilities.has('next'), color: expanded ? colors.text : colors.muted, iconSize: expanded ? 28 : 20}),
        iconButton(`${prefix}-repeat`, 'repeat', 'Repeat', 'repeat', {enabled: capabilities.has('repeat'), color: colors.muted, iconSize: 18}),
      ]);
      const metadata = node(`${prefix}-playing-meta`, 'Column', {weight: 1, spacing: 3, action: 'open-player'}, [
        text(`${prefix}-playing-title`, track.title || 'Nothing playing', expanded ? 22 : 14, {maxLines: 1, fontWeight: 700}),
        text(`${prefix}-playing-artist`, track.artist || '', expanded ? 15 : 12, {color: colors.muted, maxLines: 1}),
      ]);
      if (wide && !expanded) {
        return node(`${prefix}-player`, 'Row', {fillWidth: true, height: 88, padding: 12, spacing: 20, background: colors.base}, [
          node(`${prefix}-desktop-track`, 'Row', {weight: 1, spacing: 12, action: 'open-player'}, [
            node(`${prefix}-playing-art`, 'Artwork', {url: track.artwork || '', description: track.title, width: 56, height: 56, radius: 4}),
            metadata,
          ]),
          node(`${prefix}-desktop-center`, 'Column', {weight: 1.5, spacing: 0}, [controls, seek]),
          node(`${prefix}-desktop-options`, 'Row', {weight: 1, spacing: 4, horizontalArrangement: 'end'}, [
            iconButton(`${prefix}-queue`, 'queue', 'Queue', 'player-queue', {color: colors.muted, iconSize: 20}),
            iconButton(`${prefix}-expand`, 'expand', 'Now playing', 'open-player', {color: colors.muted, iconSize: 20}),
          ]),
        ]);
      }
      if (!expanded) {
        return node(`${prefix}-player-wrap`, 'Column', {fillWidth: true, padding: 8, spacing: 0}, [
          node(`${prefix}-player`, 'Column', {fillWidth: true, background: colors.raised, radius: 8, padding: 8, spacing: 4}, [
            node(`${prefix}-player-top`, 'Row', {fillWidth: true, spacing: 10}, [
              node(`${prefix}-playing-art`, 'Artwork', {url: track.artwork || '', description: track.title, width: 42, height: 42, radius: 4, action: 'open-player'}),
              metadata,
              toggle,
              iconButton(`${prefix}-mini-next`, 'next', 'Next', 'next', {enabled: capabilities.has('next'), color: colors.text, iconSize: 20, width: 36, height: 36}),
            ]),
            node(`${prefix}-progress`, 'Progress', {fillWidth: true, height: 2, value: duration > 0 ? Math.min(1, value.position / duration) : 0, color: colors.text, trackColor: '#33FFFFFF'}),
          ]),
        ]);
      }
      return node(`${prefix}-player`, 'LazyColumn', {fillWidth: true, weight: 1, padding: wide ? 48 : 22, spacing: 18, horizontalAlignment: 'center', background: '#181818'}, [
        node(`${prefix}-player-heading`, 'Row', {fillWidth: true, spacing: 8}, [
          iconButton(`${prefix}-close`, 'down', 'Close player', 'close-player'),
          text(`${prefix}-now-playing`, 'NOW PLAYING', 12, {weight: 1, fontWeight: 700, color: colors.muted}),
          iconButton(`${prefix}-queue`, 'queue', 'Queue', 'player-queue'),
        ]),
        node(`${prefix}-large-art`, 'Artwork', {url: track.artwork || '', description: track.title, ...(wide ? {width: 400, height: 400} : {fillWidth: true, aspectRatio: 1}), radius: 8}),
        node(`${prefix}-player-meta-row`, 'Row', {fillWidth: true}, [metadata]),
        seek,
        controls,
        text(`${prefix}-album`, track.album || '', 13, {color: colors.muted, fillWidth: true}),
      ]);
    }

    function renderSettings() {
      if (stopped) return;
      serial = 0;
      const s = state.adminSettings;
      const items = s?.settings || [];
      const tree = node('mf-settings', 'LazyColumn', {fillSize: true, padding: 20, spacing: 12, background: colors.base}, [
        node('mf-settings-top', 'Row', {fillWidth: true, spacing: 12}, [
          iconButton('mf-settings-back', 'back', 'Back', action(() => host.navigate('home').catch(report))),
          text('mf-settings-title', 'Settings', 24, {weight: 1, fontWeight: 700}),
          button('mf-settings-plugins', 'Plugins', action(() => host.navigate('plugins').catch(report)), {color: colors.accent}),
          node('mf-settings-clearance', 'Spacer', {width: 40}),
        ]),
        ...items.map((item, idx) =>
          node(`mf-set-${idx}`, 'Row', {fillWidth: true, padding: 12, background: colors.panel, radius: 10, spacing: 10}, [
            node(`mf-set-meta-${idx}`, 'Column', {weight: 1, spacing: 2}, [
              text(`mf-set-cat-${idx}`, item.category, 11, {color: colors.accent, fontWeight: 700}),
              text(`mf-set-label-${idx}`, item.title, 15, {fontWeight: 600}),
            ]),
            item.type === 'boolean'
              ? node(`mf-set-sw-${idx}`, 'Switch', {
                  checked: String(item.value === true),
                  action: action(async () => {
                    state.adminSettings = await host.call('ui.settings/v1', {action: 'set', key: item.key, value: !item.value});
                    renderSettings();
                  }),
                })
              : item.type === 'select'
                ? button(
                    `mf-set-opt-${idx}`,
                    String(item.value),
                    action(async () => {
                      const opts = item.options || [];
                      const next = opts[(opts.indexOf(item.value) + 1) % Math.max(1, opts.length)];
                      state.adminSettings = await host.call('ui.settings/v1', {action: 'set', key: item.key, value: next});
                      renderSettings();
                    }),
                    {color: colors.accent},
                  )
                : text(`mf-set-val-${idx}`, String(item.value || '—'), 13, {color: colors.muted}),
          ]),
        ),
        button(
          'mf-settings-original',
          'Use original MetroFuse UI',
          action(() => host.call('ui.settings/v1', {action: 'openOriginal'}).catch(report)),
          {color: colors.muted},
        ),
      ]);
      host.ui('ui.settings', tree);
    }

    function renderPlugins() {
      if (stopped) return;
      serial = 0;
      const p = state.adminPlugins;
      const list = p?.plugins || [];
      const apps = p?.uiApps || [];
      const tree = node('mf-plugins', 'LazyColumn', {fillSize: true, padding: 20, spacing: 12, background: colors.base}, [
        node('mf-plugins-top', 'Row', {fillWidth: true, spacing: 12}, [
          iconButton('mf-plugins-back', 'back', 'Back', action(() => host.navigate('home').catch(report))),
          text('mf-plugins-title', 'Plugins', 24, {weight: 1, fontWeight: 700}),
          button('mf-plugins-settings', 'Settings', action(() => host.navigate('settings').catch(report)), {color: colors.accent}),
          node('mf-plugins-clearance', 'Spacer', {width: 40}),
        ]),
        node('mf-plugins-tabs', 'Row', {spacing: 8}, [
          button('mf-tab-installed', 'Installed', action(() => { state.pluginTab = 'installed'; renderPlugins(); }), {color: state.pluginTab === 'installed' ? colors.accent : colors.muted}),
          button('mf-tab-ui', 'App Interface', action(() => { state.pluginTab = 'ui'; renderPlugins(); }), {color: state.pluginTab === 'ui' ? colors.accent : colors.muted}),
        ]),
        ...(state.pluginTab === 'ui'
          ? [
              ...apps.map((app, idx) =>
                node(`mf-uiapp-${idx}`, 'Row', {fillWidth: true, padding: 12, background: colors.panel, radius: 10, spacing: 10}, [
                  text(`mf-uiapp-name-${idx}`, app.name, 15, {weight: 1, fontWeight: 600}),
                  button(
                    `mf-uiapp-use-${idx}`,
                    app.active ? 'Active' : 'Activate',
                    action(async () => {
                      state.adminPlugins = await host.call('ui.plugins/v1', {action: 'setUiApp', id: app.id});
                      renderPlugins();
                    }),
                    {color: app.active ? colors.accent : colors.text},
                  ),
                ]),
              ),
              button('mf-uiapp-original', 'Use original MetroFuse interface', action(() => host.call('ui.plugins/v1', {action: 'setSafeMode', enabled: true}).catch(report)), {color: colors.muted}),
            ]
          : list.map((item, idx) =>
              node(`mf-plug-${idx}`, 'Row', {fillWidth: true, padding: 12, background: colors.panel, radius: 10, spacing: 10}, [
                node(`mf-plug-meta-${idx}`, 'Column', {weight: 1, spacing: 2}, [
                  text(`mf-plug-name-${idx}`, `${item.name} · v${item.version}`, 15, {fontWeight: 700}),
                  text(`mf-plug-sub-${idx}`, `${item.author} · ${item.state}`, 12, {color: colors.muted}),
                ]),
                node(`mf-plug-sw-${idx}`, 'Switch', {
                  checked: String(Boolean(item.enabled)),
                  action: action(async () => {
                    state.adminPlugins = await host.call('ui.plugins/v1', {action: item.enabled ? 'disable' : 'enable', id: item.id});
                    renderPlugins();
                  }),
                }),
              ]),
            )),
      ]);
      host.ui('ui.plugins', tree);
    }

    async function selectFrontend(owner) {
      try {
        state.frontends = await host.call('ui.frontends/v1', {select: owner});
        state.showFrontends = false;
        await loadHome();
      } catch (_) {
        state.showFrontends = false;
        render();
      }
    }

    function render() {
      if (stopped) return;
      // Action IDs are bounded to the current tree and reused by the host on refresh.
      serial = 0;
      const npPanel = nowPlayingSidebar();
      const wide = node('wide-shell', 'Column', {fillSize: true, background: colors.base}, [
        ...(state.expanded
          ? []
          : [
              node('wide-global-header', 'Row', {fillWidth: true, height: 64, spacing: 12, padding: 8}, [
                iconButton('wide-brand', 'library', 'MetroFlow', 'page-home', {iconSize: 28, color: colors.accent}),
                iconButton('wide-global-home', 'home', 'Home', 'page-home', {background: colors.elevated, radius: 24, width: 44, height: 44}),
                node('wide-global-search', 'TextField', {appearance: 'search', label: 'What do you want to play?', action: 'search', value: state.query, width: 380, height: 44, background: colors.elevated, radius: 24}),
                node('wide-global-right-space', 'Spacer', {weight: 1}),
                button('wide-global-source', state.source.title || 'Offline', 'open-sources', {background: colors.elevated, color: colors.text, radius: 20, height: 34}),
                iconButton('wide-global-settings', 'settings', 'Plugins', 'plugins', {color: colors.muted, iconSize: 20}),
                button('wide-global-profile', state.source.accountName?.slice(0, 1).toUpperCase() || 'M', 'settings', {background: colors.accent, color: colors.base, radius: 24, width: 34, height: 34}),
                node('wide-recovery-clearance', 'Spacer', {width: 40}),
              ]),
            ]),
        ...(state.expanded
          ? []
          : [
              node('wide-main', 'Row', {weight: 1, fillWidth: true, spacing: 8, padding: 8}, [
                navigation('wide', true),
                content('wide', true),
                ...(npPanel ? [npPanel] : []),
              ]),
            ]),
        player('wide', true),
      ]);
      const compact = node('compact-shell', 'Column', {fillSize: true, background: colors.base}, [
        ...(state.expanded ? [] : [content('compact', false)]),
        player('compact', false),
        ...(state.expanded ? [] : [navigation('compact', false)]),
      ]);
      host.ui('ui.app', node('metroflow', 'Adaptive', {fillSize: true, breakpoint: 840}, [wide, compact]));
    }

    async function loadLibrary(reset = false, direction = 0) {
      if (reset) {
        libraryEpoch++;
        state.tracks = [];
        state.offset = 0;
        state.more = true;
      }
      const epoch = libraryEpoch;
      if (!reset && state.loading) return;
      state.loading = true;
      state.message = '';
      render();
      try {
        const offset = Math.max(0, state.offset + direction * 50);
        const tracks = await host.library(offset, 50);
        if (stopped || epoch !== libraryEpoch) return;
        state.offset = offset;
        state.more = tracks.length === 50;
        state.tracks = tracks;
      } catch (_) {
        if (epoch === libraryEpoch) state.message = 'Could not load your library. Press Refresh to retry.';
      }
      if (stopped || epoch !== libraryEpoch) return;
      state.loading = false;
      render();
    }

    async function loadHome() {
      const ticket = ++request;
      state.loading = true;
      state.message = '';
      render();
      try {
        const source = await host.call('ui.source/v1');
        if (stopped || ticket !== request) return;
        state.source = source;
        const response = await host.call('ui.home/v1');
        if (!stopped && ticket === request) state.shelves = response.shelves || [];
      } catch (_) {
        if (ticket === request) state.message = 'Could not load Home. Press Refresh to retry.';
      }
      if (!stopped && ticket === request) {
        state.loading = false;
        render();
      }
    }

    async function search(query) {
      const ticket = ++request;
      state.page = 'search';
      state.catalogResults = {};
      state.query = query.trim();
      state.loading = true;
      state.results = [];
      state.message = '';
      render();
      try {
        const response = await host.call('ui.search/v1', {query: state.query, limit: 60});
        if (!stopped && ticket === request) {
          state.results = response.results?.tracks || [];
          state.catalogResults = response.results || {};
        }
      } catch (_) {
        if (ticket === request) state.message = 'Search failed. Check your source and try again.';
      }
      if (!stopped && ticket === request) {
        state.loading = false;
        render();
      }
    }

    for (const page of ['home', 'search', 'library', 'queue']) {
      host.action(`page-${page}`, () => {
        request++;
        libraryEpoch++;
        state.loading = false;
        state.page = page;
        state.message = '';
        if (page === 'library') return loadLibrary(true);
        if (page === 'home') return loadHome();
        if (page === 'search') {
          state.results = [];
          state.catalogResults = {};
        }
        render();
      });
    }
    for (const kind of ['all', 'track', 'album', 'artist', 'playlist']) {
      host.action(`filter-${kind}`, () => {
        state.filter = kind;
        render();
      });
    }
    host.action('open-player', () => {
      state.expanded = true;
      render();
    });
    host.action('close-player', () => {
      state.expanded = false;
      render();
    });
    host.action('toggle-np-panel', () => {
      state.nowPlayingPanel = !state.nowPlayingPanel;
      render();
    });
    host.action('open-sources', async () => {
      try {
        state.frontends = await host.call('ui.frontends/v1');
      } catch (_) {}
      state.showFrontends = true;
      render();
    });
    host.action('close-sources', () => {
      state.showFrontends = false;
      render();
    });
    host.action('player-queue', () => {
      state.expanded = false;
      state.page = 'queue';
      render();
    });
    host.action('plugins', () => host.navigate('plugins').catch(report));
    host.action('settings', () => host.navigate('settings').catch(report));
    host.action('search', search);
    host.action('refresh', () => (state.page === 'home' ? loadHome() : state.page === 'library' ? loadLibrary(true) : search(state.query)));
    host.action('more', () => loadLibrary(false, 1));
    host.action('back', () => loadLibrary(false, -1));
    host.action('toggle', () => host.player('toggle').catch(report));
    host.action('previous', () => host.player('previous').catch(report));
    host.action('next', () => host.player('next').catch(report));
    host.action('shuffle', () => host.player('shuffle').catch(report));
    host.action('repeat', () => host.player('repeat').catch(report));
    host.action('seek', value => {
      const fraction = Number(value);
      const duration = Number(state.player.track.duration || 0);
      if (Number.isFinite(fraction) && duration > 0) host.player('seek', Math.round(Math.min(1, Math.max(0, fraction)) * duration)).catch(report);
    });

    subscriptions.push(
      host.on('playback_changed', playback => {
        const previousId = state.player.track?.id;
        state.player = playback;
        if (playback.track?.id !== previousId) {
          render();
          return;
        }
        const duration = Number(playback.track?.duration || 0);
        const fraction = duration > 0 ? Math.min(1, playback.position / duration) : 0;
        const iconName = playback.playing ? 'pause' : 'play';
        const label = playback.playing ? 'Pause' : 'Play';
        host.patchMany({
          'wide-toggle': {icon: iconName, description: label},
          'wide-elapsed': {text: time(playback.position)},
          'wide-seek': {value: String(fraction), enabled: String(duration > 0 && capabilities.has('seek'))},
          'compact-toggle': {icon: iconName, description: label},
          'compact-progress': {value: String(fraction)},
          'compact-elapsed': {text: time(playback.position)},
          'compact-seek': {value: String(fraction), enabled: String(duration > 0 && capabilities.has('seek'))},
        });
      }),
    );

    subscriptions.push(
      host.on('ui_home_changed', () => {
        if (state.page === 'home') loadHome();
      }),
    );

    subscriptions.push(
      host.on('navigation_changed', async destination => {
        if (destination === 'settings') {
          try {
            state.adminSettings = await host.call('ui.settings/v1', {action: 'snapshot'});
          } catch (_) {}
          renderSettings();
        } else if (destination === 'plugins') {
          try {
            state.adminPlugins = await host.call('ui.plugins/v1', {action: 'snapshot'});
          } catch (_) {}
          renderPlugins();
        }
      }),
    );

    subscriptions.push(
      host.on('library_changed', () => {
        host.library(0, 20).then(tracks => {
          if (stopped) return;
          state.sidebarTracks = tracks;
          render();
        }).catch(() => {});
        if (state.page === 'library') loadLibrary(true);
      }),
    );

    render();
    renderSettings();
    renderPlugins();
    loadHome();
    host.library(0, 20).then(tracks => {
      if (stopped) return;
      state.sidebarTracks = tracks;
      render();
    }).catch(() => {});
    host.call('ui.settings/v1', {action: 'snapshot'}).then(res => {
      if (stopped) return;
      state.adminSettings = res;
      renderSettings();
    }).catch(() => {});
    host.call('ui.plugins/v1', {action: 'snapshot'}).then(res => {
      if (stopped) return;
      state.adminPlugins = res;
      renderPlugins();
    }).catch(() => {});

    return {
      dispose() {
        stopped = true;
        subscriptions.splice(0).forEach(stop => stop());
      },
    };
  },
  dispose() {},
};
