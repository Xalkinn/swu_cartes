(() => {
    'use strict';
    const header = document.querySelector('.site-header');
    if (!header || header.dataset.navigationReady) return;
    const button = header.querySelector('.navigation-bascule');
    const nav = header.querySelector('#navigation-principale');
    if (!button || !nav) return;
    header.dataset.navigationReady = 'true';
    const mobile = matchMedia('(max-width: 1000px)');
    const hover = matchMedia('(hover: hover) and (pointer: fine)');
    const groups = [...nav.querySelectorAll('.site-menu > li > details.menu-groupe')];
    const nested = [...nav.querySelectorAll('details.menu-ajout')];
    const all = [...groups, ...nested];
    const timers = new WeakMap();
    const shut = group => { group.open = false; group.querySelectorAll('details').forEach(child => { child.open = false; }); };
    const closeGroups = () => groups.forEach(shut);
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
    const fit = group => {
        group.classList.remove('menu-ajout-gauche');
        if (!group.open || mobile.matches) return;
        const panel = group.querySelector(':scope > ul');
        if (panel?.getBoundingClientRect().right > innerWidth - 12) group.classList.add('menu-ajout-gauche');
    };
    button.addEventListener('click', () => {
        const open = button.getAttribute('aria-expanded') !== 'true';
        button.setAttribute('aria-expanded', String(open));
        nav.hidden = !open;
        if (!open) closeGroups();
    });
    groups.forEach(group => group.addEventListener('toggle', () => {
        // Le sous-niveau « Ajout » ne doit jamais refermer son parent.
        if (group.open) groups.forEach(other => { if (other !== group) shut(other); });
        else group.querySelectorAll('details').forEach(child => { child.open = false; });
    }));
    nested.forEach(group => group.addEventListener('toggle', () => fit(group)));
    all.forEach(group => {
        group.addEventListener('pointerenter', () => {
            clearTimeout(timers.get(group));
            if (mobile.matches || !hover.matches) return;
            group.open = true;
            if (group.matches('.menu-ajout')) fit(group);
        });
        group.addEventListener('pointerleave', () => {
            if (mobile.matches || !hover.matches) return;
            timers.set(group, setTimeout(() => {
                if (!group.matches(':hover') && !group.contains(document.activeElement)) shut(group);
            }, 180));
        });
    });
    document.addEventListener('click', event => { if (!header.contains(event.target)) close(); });
    nav.addEventListener('click', event => { if (event.target.closest('a') && mobile.matches) close(); });
    header.addEventListener('keydown', event => {
        if (event.key === 'Escape') {
            const group = event.target.closest('details[open]');
            if (group) { shut(group); group.querySelector('summary')?.focus(); }
            else close(true);
        }
        const group = event.target.closest('.menu-ajout');
        if (group && event.target.matches('summary') && event.key === 'ArrowRight') {
            event.preventDefault(); group.open = true; fit(group); group.querySelector('a')?.focus();
        }
        if (group && event.key === 'ArrowLeft') {
            event.preventDefault(); shut(group); group.querySelector('summary').focus();
        }
    });
    mobile.addEventListener('change', viewport);
    window.addEventListener('resize', () => nested.forEach(fit));
    viewport();
})();
