(() => {
  const page = document.querySelector(".page");
  if (!page) return;
  page.dataset.screen = "home";

  const profiles = [
    { name: "Subway Surfers", packageName: "com.kiloo.subwaysurf", orientation: "PORTRAIT", tracker: "1-Hand", mapping: "Lane Swipe" },
    { name: "Fruit Ninja", packageName: "com.halfbrick.fruitninja", orientation: "LANDSCAPE", tracker: "1-Hand", mapping: "Air Slash" },
    { name: "Asphalt 9: Legends", packageName: "com.gameloft.android.ANMP.GloftA9HM", orientation: "LANDSCAPE", tracker: "2-Hand Wheel", mapping: "Steer Buttons" }
  ];

  const state = {
    setupComplete: false,
    profileIndex: 0,
    screen: "home",
    telemetry: false,
    haptics: true,
    engineRunning: false,
    calibrationTimer: null,
    playTimer: null,
    playSeconds: 0,
    calibrationSeconds: 60,
    noticeTimer: null
  };

  const originalHome = page.innerHTML;

  function showNotice(message) {
    let notice = document.querySelector(".demo-notice");
    if (!notice) {
      notice = document.createElement("div");
      notice.className = "demo-notice";
      notice.setAttribute("role", "status");
      notice.style.cssText = "position:fixed;left:50%;bottom:22px;z-index:5;transform:translateX(-50%);max-width:calc(100% - 32px);padding:12px 16px;border:1px solid rgba(0,229,255,.32);border-radius:12px;background:#121830;color:#e6edf7;box-shadow:0 12px 36px #0008;font-size:13px;text-align:center";
      document.body.append(notice);
    }
    notice.textContent = message;
    notice.hidden = false;
    clearTimeout(state.noticeTimer);
    state.noticeTimer = setTimeout(() => { notice.hidden = true; }, 2800);
  }

  function cleanupTimers() {
    clearInterval(state.calibrationTimer);
    clearInterval(state.playTimer);
    state.calibrationTimer = null;
    state.playTimer = null;
  }

  function escapeText(text) {
    return String(text).replace(/[&<>"']/g, (character) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;"
    })[character]);
  }

  function screenShell(title, subtitle, content) {
    return `
      <section class="screen-view" aria-label="${escapeText(title)}">
        <div class="screen-top">
          <button class="back-button" type="button" aria-label="Back to home" data-action="home">‹</button>
          <div>
            <h2 class="screen-title">${escapeText(title)}</h2>
            <p class="screen-subtitle">${escapeText(subtitle)}</p>
          </div>
        </div>
        ${content}
        <p class="demo-caption">Browser demo only. Actions here simulate the Android app; they do not change device permissions or control games.</p>
      </section>`;
  }

  function showScreen(name, title, subtitle, content) {
    cleanupTimers();
    state.screen = name;
    page.dataset.screen = name;
    page.querySelector(".screen-view")?.remove();
    page.insertAdjacentHTML("beforeend", screenShell(title, subtitle, content));
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function renderHome() {
    cleanupTimers();
    state.screen = "home";
    page.dataset.screen = "home";
    page.innerHTML = originalHome;
    updateHome();
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function updateHome() {
    const profile = profiles[state.profileIndex];
    const heading = page.querySelector(".game-card .game-name");
    const packageName = page.querySelector(".game-card .package-name");
    const orientation = page.querySelector(".game-card .orientation");
    const tags = page.querySelector(".game-card .tags");
    const playButton = page.querySelector('[data-action="play"]');
    const readiness = page.querySelector(".readiness");
    const readinessTitle = page.querySelector(".readiness-title");
    const readinessDetail = page.querySelector(".readiness-detail");
    const warning = page.querySelector(".warning-icon");
    const chips = page.querySelectorAll(".status-chip");
    const engine = page.querySelector(".engine-state");

    if (heading) heading.textContent = profile.name;
    if (packageName) packageName.textContent = profile.packageName;
    if (orientation) orientation.textContent = profile.orientation;
    if (tags) {
      tags.innerHTML = `<span class="profile-tag">${escapeText(profile.tracker)}</span><span class="profile-tag">${escapeText(profile.mapping)}</span>`;
    }
    if (playButton) {
      playButton.classList.toggle("is-unready", !state.setupComplete);
      const label = state.engineRunning ? "SESSION RUNNING" : state.setupComplete ? "PLAY NOW" : "COMPLETE SETUP TO PLAY";
      playButton.lastChild.textContent = ` ${label}`;
    }
    if (readiness) {
      readiness.style.borderColor = state.setupComplete ? "rgba(0, 230, 118, .32)" : "";
      readiness.style.background = state.setupComplete ? "linear-gradient(105deg, rgba(0, 230, 118, .09), rgba(0, 230, 118, .025))" : "";
    }
    if (readinessTitle) {
      readinessTitle.textContent = state.setupComplete ? "All Systems Operational" : "Setup Incomplete";
      readinessTitle.style.color = state.setupComplete ? "var(--green)" : "";
    }
    if (readinessDetail) {
      readinessDetail.textContent = state.setupComplete
        ? "Demo setup complete. You can try the simulated play screen."
        : "Tap to open Setup Wizard and grant required permissions.";
    }
    if (warning) {
      warning.textContent = state.setupComplete ? "✓" : "!";
      warning.style.color = state.setupComplete ? "var(--green)" : "";
      warning.style.borderColor = state.setupComplete ? "rgba(0, 230, 118, .27)" : "";
      warning.style.background = state.setupComplete ? "rgba(0, 230, 118, .1)" : "";
    }
    chips.forEach((chip, index) => {
      const ready = state.setupComplete || index === 1;
      chip.classList.toggle("ready", ready);
      const label = chip.querySelector("span:last-child");
      if (label) {
        label.textContent = ready ? "READY" : "REQUIRED";
        label.style.color = ready ? "var(--green)" : "";
      }
    });
    if (engine) {
      engine.textContent = state.engineRunning ? "ACTIVE" : "IDLE";
      engine.style.color = state.engineRunning ? "var(--green)" : "";
      engine.style.borderColor = state.engineRunning ? "rgba(0, 230, 118, .35)" : "";
      engine.style.background = state.engineRunning ? "rgba(0, 230, 118, .08)" : "";
    }
  }

  function showSetup() {
    const content = `
      <section class="screen-panel">
        <h3>Permission checklist</h3>
        <p>These Android-only permissions cannot be granted from a browser. Use this demo to preview the setup flow.</p>
        <div class="setting-row"><span>Camera access</span><strong>${state.setupComplete ? "Ready" : "Needs setup"}</strong></div>
        <div class="setting-row"><span>Accessibility service</span><strong>${state.setupComplete ? "Ready" : "Needs setup"}</strong></div>
        <div class="setting-row"><span>Battery optimization</span><strong>${state.setupComplete ? "Ready" : "Needs setup"}</strong></div>
        <div class="screen-actions">
          <button class="button" type="button" data-action="complete-setup">${state.setupComplete ? "Reset demo setup" : "Simulate completed setup"}</button>
          <button class="button" type="button" data-action="home">Back to home</button>
        </div>
      </section>
      <section class="screen-panel">
        <h3>On your Android device</h3>
        <p>The real app needs camera permission, its accessibility service, and battery-optimization exemption. The browser preview cannot request these system permissions.</p>
      </section>`;
    showScreen("setup", "Setup Wizard", "Permissions and device readiness", content);
  }

  function showLibrary() {
    const options = profiles.map((profile, index) => {
      const selected = index === state.profileIndex;
      return `
        <article class="list-card">
          <div><h3>${escapeText(profile.name)}${selected ? " · Active" : ""}</h3>
            <p>${escapeText(profile.orientation)} · ${escapeText(profile.tracker)} · ${escapeText(profile.mapping)}</p></div>
          <button class="button" type="button" data-action="select-game" data-index="${index}">${selected ? "Selected" : "Use profile"}</button>
        </article>`;
    }).join("");
    const content = `
      <section class="screen-panel">
        <h3>Game profiles</h3>
        <p>Choose a sample profile to update the home screen. Profiles are demo data.</p>
        ${options}
      </section>`;
    showScreen("library", "Game Library", "Browse and choose a motion profile", content);
  }

  function showCommunity() {
    const content = `
      <section class="screen-panel">
        <h3>Community profiles</h3>
        <p>Preview community-shared configurations. Cloud downloads are not connected in this browser demo.</p>
        <article class="list-card"><div><h3>Subway Surfers · Beginner</h3><p>Lane Swipe · 1-Hand · Demo community profile</p></div><button class="button" type="button" data-action="select-game" data-index="0">Use profile</button></article>
        <article class="list-card"><div><h3>Fruit Ninja · Classic</h3><p>Air Slash · 1-Hand · Demo community profile</p></div><button class="button" type="button" data-action="select-game" data-index="1">Use profile</button></article>
      </section>`;
    showScreen("community", "Community", "Discover shared game profiles", content);
  }

  function showSettings() {
    const content = `
      <section class="screen-panel">
        <h3>Preferences</h3>
        <p>Toggle these local demo preferences. They do not change Android device settings.</p>
        <div class="setting-row"><span>Anonymous telemetry</span><button class="switch" type="button" role="switch" aria-checked="${state.telemetry}" data-action="toggle-telemetry">${state.telemetry ? "On" : "Off"}</button></div>
        <div class="setting-row"><span>Haptic feedback</span><button class="switch" type="button" role="switch" aria-checked="${state.haptics}" data-action="toggle-haptics">${state.haptics ? "On" : "Off"}</button></div>
      </section>`;
    showScreen("settings", "Settings", "Telemetry and interaction preferences", content);
  }

  function showAbout() {
    const content = `
      <section class="screen-panel">
        <h3>Touchless play, powered by gestures.</h3>
        <p>ZORBI is a front-camera gesture-controller Android app. Configure a game profile, calibrate motion, and explore touchless controls.</p>
      </section>
      <section class="screen-panel">
        <h3>About this page</h3>
        <p>This is a standalone browser preview, not the Android app. Navigation and profile changes are simulated; camera tracking, permissions, and game input are not available here.</p>
      </section>`;
    showScreen("about", "About ZORBI", "Front camera gesture controller", content);
  }

  function showCalibration() {
    const content = `
      <section class="screen-panel">
        <h3 data-calibration-title>Ready to calibrate</h3>
        <p data-calibration-description>This browser demo can preview the calibration countdown, but it cannot read your camera or measure hand movement.</p>
        <div class="screen-actions"><button class="button" type="button" data-action="start-calibration">Start demo countdown (60s)</button></div>
      </section>`;
    showScreen("calibrate", "Calibrate Profile", profiles[state.profileIndex].name, content);
  }

  function startCalibration(button) {
    state.calibrationSeconds = 60;
    button.textContent = `Demo countdown: ${state.calibrationSeconds}s`;
    button.disabled = true;
    const title = page.querySelector("[data-calibration-title]");
    const description = page.querySelector("[data-calibration-description]");
    if (title) title.textContent = "Calibration in progress";
    if (description) description.textContent = "Simulated countdown only. No camera data is being captured.";
    state.calibrationTimer = setInterval(() => {
      state.calibrationSeconds -= 1;
      if (!button.isConnected) {
        clearInterval(state.calibrationTimer);
        state.calibrationTimer = null;
        return;
      }
      if (state.calibrationSeconds > 0) {
        button.textContent = `Demo countdown: ${state.calibrationSeconds}s`;
        return;
      }
      clearInterval(state.calibrationTimer);
      state.calibrationTimer = null;
      button.textContent = "Demo calibration complete";
      if (title) title.textContent = "Calibration preview complete";
      if (description) description.textContent = "This was a simulated countdown. Real calibration requires the Android app and camera access.";
    }, 1000);
  }

  function showPlay() {
    state.engineRunning = true;
    state.playSeconds = 0;
    const content = `
      <section class="screen-panel">
        <h3>Demo session · ${escapeText(profiles[state.profileIndex].name)}</h3>
        <p data-play-status>Simulated play session. Camera tracking and game controls are not active.</p>
        <div class="setting-row"><span>Session time</span><strong data-play-time>00:00</strong></div>
        <div class="setting-row"><span>Gesture tracking</span><strong>Preview only</strong></div>
        <div class="screen-actions"><button class="button" type="button" data-action="stop-play">End demo session</button></div>
      </section>`;
    showScreen("play", "Play", "Touchless play · demo mode", content);
    state.playTimer = setInterval(() => {
      state.playSeconds += 1;
      const display = page.querySelector("[data-play-time]");
      if (display) {
        const minutes = String(Math.floor(state.playSeconds / 60)).padStart(2, "0");
        const seconds = String(state.playSeconds % 60).padStart(2, "0");
        display.textContent = `${minutes}:${seconds}`;
      }
    }, 1000);
  }

  function handleAction(element) {
    const action = element.dataset.action;
    if (!action) return;

    switch (action) {
      case "home":
        state.engineRunning = false;
        renderHome();
        break;
      case "setup":
        showSetup();
        break;
      case "complete-setup":
        state.setupComplete = !state.setupComplete;
        showNotice(state.setupComplete ? "Demo setup complete. Play is now simulated." : "Demo setup reset.");
        showSetup();
        break;
      case "library":
        showLibrary();
        break;
      case "select-game":
        state.profileIndex = Number(element.dataset.index);
        if (!Number.isInteger(state.profileIndex) || !profiles[state.profileIndex]) {
          state.profileIndex = 0;
          showNotice("That profile could not be selected.");
          break;
        }
        showNotice(`${profiles[state.profileIndex].name} selected.`);
        renderHome();
        break;
      case "community":
        showCommunity();
        break;
      case "settings":
        showSettings();
        break;
      case "about":
        showAbout();
        break;
      case "calibrate":
        showCalibration();
        break;
      case "start-calibration":
        startCalibration(element);
        break;
      case "play":
        if (element.classList.contains("is-unready")) {
          showSetup();
        } else {
          showPlay();
        }
        break;
      case "stop-play":
        state.engineRunning = false;
        renderHome();
        showNotice("Demo session ended.");
        break;
      case "toggle-telemetry":
        state.telemetry = !state.telemetry;
        showSettings();
        break;
      case "toggle-haptics":
        state.haptics = !state.haptics;
        showSettings();
        break;
      default:
        showNotice("This action is not available in the browser demo.");
    }
  }

  page.addEventListener("click", (event) => {
    const action = event.target.closest("[data-action]");
    if (action && page.contains(action)) handleAction(action);
  });

  page.addEventListener("keydown", (event) => {
    if (event.key !== "Enter" && event.key !== " ") return;
    const action = event.target.closest('[role="button"][data-action]');
    if (!action || page.contains(event.target.closest("button"))) return;
    event.preventDefault();
    handleAction(action);
  });

  updateHome();
})();
