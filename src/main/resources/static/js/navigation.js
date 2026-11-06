(() => {
    'use strict';
    const header = document.querySelector('.site-header');
    if (!header || header.dataset.navigationReady) return;
    const button = header.querySelector('.navigation-bascule');
    const nav = header.querySelector('#navigation-principale');
    if (!button || !nav) return;
    header.dataset.navigationReady = 'true';
    const mobile = matchMedia('(max-width: 1000px)');
    const groups = [...nav.querySelectorAll('details.menu-groupe')];
    const closeGroups = () => groups.forEach(group => {
        group.open = false;
    });
    const close = (focus = false) => {
        button.setAttribute('aria-expanded', 'false');
        if (mobile.matches) nav.hidden = true;
        closeGroups();
        if (focus && mobile.matches) button.focus();
    };
    const viewport = () => {
        const hadFocus = nav.contains(document.activeElement);
        button.hidden = !mobile.matches;
        nav.hidden = mobile.matches;
        button.setAttribute('aria-expanded', 'false');
        closeGroups();
        if (hadFocus && mobile.matches) button.focus();
    };
    button.addEventListener('click', () => {
        const open = button.getAttribute('aria-expanded') !== 'true';
        button.setAttribute('aria-expanded', String(open));
        nav.hidden = !open;
        if (!open) closeGroups();
    });
    groups.forEach(group => group.addEventListener('toggle', () => {
        if (group.open) groups.forEach(other => {
            if (other !== group) other.open = false;
        });
    }));
    document.addEventListener('click', event => {
        if (header.contains(event.target)) return;
        close();
    });
    nav.addEventListener('click', event => {
        if (event.target.closest('a') && mobile.matches) close();
    });
    header.addEventListener('keydown', event => {
        if (event.key !== 'Escape') return;
        const group = event.target.closest('details[open]');
        if (group) {
            group.open = false;
            group.querySelector('summary')?.focus();
        } else close(true);
    });
    mobile.addEventListener('change', viewport);
    viewport();
})();
