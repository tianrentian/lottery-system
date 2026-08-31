(function () {
  'use strict';

  var SESSION_KEY = 'lottery_demo_visit_session';
  var DISABLED_KEY = 'lottery_demo_tracking_disabled';
  var SESSION_TIMEOUT = 30 * 60 * 1000;
  var ENGAGED_TIME = 60 * 1000;

  function noOperation() {}

  if (localStorage.getItem(DISABLED_KEY) === '1') {
    window.demoVisitTracker = { report: noOperation };
    return;
  }

  function createUuid() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
      return window.crypto.randomUUID();
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (char) {
      var random = Math.random() * 16 | 0;
      var value = char === 'x' ? random : (random & 0x3 | 0x8);
      return value.toString(16);
    });
  }

  function readSession() {
    try {
      return JSON.parse(localStorage.getItem(SESSION_KEY));
    } catch (error) {
      return null;
    }
  }

  function saveSession(session) {
    localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  }

  var now = Date.now();
  var session = readSession();
  if (!session || !session.sessionId || !session.lastSeenAt ||
      now - session.lastSeenAt > SESSION_TIMEOUT) {
    session = {
      sessionId: createUuid(),
      startedAt: now,
      lastSeenAt: now,
      reportedEvents: {},
      reportedErrors: 0
    };
  }
  session.reportedEvents = session.reportedEvents || {};
  session.reportedErrors = session.reportedErrors || 0;
  session.lastSeenAt = now;
  saveSession(session);

  function report(eventType) {
    if (eventType !== 'PAGE_ERROR' && session.reportedEvents[eventType]) {
      return;
    }
    if (eventType === 'PAGE_ERROR' && session.reportedErrors >= 5) {
      return;
    }

    if (eventType === 'PAGE_ERROR') {
      session.reportedErrors += 1;
    } else {
      session.reportedEvents[eventType] = true;
    }
    session.lastSeenAt = Date.now();
    saveSession(session);

    fetch('/demo-visits/events', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        sessionId: session.sessionId,
        eventType: eventType
      }),
      keepalive: true,
      credentials: 'same-origin'
    }).catch(noOperation);
  }

  window.demoVisitTracker = { report: report };
  report('PAGE_OPEN');

  var remainingTime = ENGAGED_TIME - (Date.now() - session.startedAt);
  if (remainingTime <= 0) {
    report('STAY_60_SECONDS');
  } else {
    window.setTimeout(function () {
      report('STAY_60_SECONDS');
    }, remainingTime);
  }

  window.addEventListener('error', function () {
    report('PAGE_ERROR');
  });
  window.addEventListener('unhandledrejection', function () {
    report('PAGE_ERROR');
  });
})();
