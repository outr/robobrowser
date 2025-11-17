const abs = (href) => {
try { return new URL(href, location.href).href; }
catch { return null; }
};

const isVisible = (el) => {
if (!(el instanceof Element)) return false;
for (let e = el; e; e = e.parentElement) {
  if (e.hidden) return false;
  const cs = getComputedStyle(e);
  if (cs.display === 'none' || cs.visibility === 'hidden' || +cs.opacity === 0) return false;
}
return el.getClientRects().length > 0;
};

const collectText = (root, { includeHidden }) => {
const texts = [];
const walker = document.createTreeWalker(
  root,
  NodeFilter.SHOW_TEXT,
  {
    acceptNode(node) {
      const value = node.nodeValue;
      if (!value || !value.trim()) return NodeFilter.FILTER_REJECT;
      const parent = node.parentElement;
      if (!parent) return NodeFilter.FILTER_REJECT;
      const tag = parent.tagName;
      if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT' || tag === 'TEMPLATE') {
        return NodeFilter.FILTER_REJECT;
      }
      if (!includeHidden && !isVisible(parent)) {
        return NodeFilter.FILTER_REJECT;
      }
      return NodeFilter.FILTER_ACCEPT;
    }
  },
  false
);

while (walker.nextNode()) {
  texts.push(
    walker.currentNode.nodeValue
      .replace(/\s+/g, ' ')
      .trim()
  );
}

// De-dupe and normalize a bit
return Array.from(new Set(texts))
  .join('\n')
  .replace(/\n{3,}/g, '\n\n')
  .trim();
};

const collectLinks = (root) => {
const out = [];
const anchors = root.querySelectorAll('a[href]');
anchors.forEach(a => {
  const hrefRaw = a.getAttribute('href');
  if (!hrefRaw) return;

  const href = abs(hrefRaw);
  if (!href) return;
  // skip junk schemes
  if (/^(javascript:|mailto:|tel:|sms:|#)/i.test(hrefRaw)) return;

  const text = (a.innerText || a.textContent || '')
    .replace(/\s+/g, ' ')
    .trim();

  out.push({
    href,
    text,
    title: a.getAttribute('title') || null,
    rel: a.getAttribute('rel') || null,
    same_origin: new URL(href).origin === location.origin
  });
});

// Optional de-dupe by href+text
const seen = new Set();
return out.filter(l => {
  const key = l.href + '|' + l.text;
  if (seen.has(key)) return false;
  seen.add(key);
  return true;
});
};

const root = document.body || document.documentElement;

const result = {
url: location.href,
title: document.title || null,
fetched_at: new Date().toISOString(),

// all text, including hidden/tabbed sections that are already in DOM
text_all: collectText(root, { includeHidden: true }),

// only text from elements that are currently visible (if you want it)
text_visible: collectText(root, { includeHidden: false }),

links: collectLinks(root)
};

return result;