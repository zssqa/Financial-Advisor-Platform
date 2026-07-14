import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

let mdInstance = null

function getMarkdown() {
    if (mdInstance) return mdInstance

    mdInstance = new MarkdownIt({
        html: false,
        breaks: true,
        linkify: true,
        highlight(str, lang) {
            if (lang && hljs.getLanguage(lang)) {
                try {
                    return '<pre><code class="hljs">' +
                        hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
                        '</code></pre>'
                } catch { /* ignore */ }
            }
            return '<pre><code class="hljs">' + mdInstance.utils.escapeHtml(str) + '</code></pre>'
        }
    })

    mdInstance.renderer.rules.table_open = () => '<table style="border-collapse:collapse;width:100%;margin:8px 0;font-size:14px;">'
    mdInstance.renderer.rules.th_open = () => '<th style="border:1px solid #d0d5dd;padding:6px 12px;background:#f2f4f7;font-weight:600;text-align:left;">'
    mdInstance.renderer.rules.td_open = () => '<td style="border:1px solid #d0d5dd;padding:6px 12px;">'

    return mdInstance
}

export function useMarkdown() {
    const md = getMarkdown()
    return {
        render(content) {
            if (!content) return ''
            return md.render(content)
        }
    }
}
