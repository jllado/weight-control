<template>
  <div v-if="visible" class="win-celebration" :class="`win-celebration--${variant}`" role="status" aria-live="polite">
    <div class="win-celebration-ring" />
    <div class="win-celebration-title">WIN</div>
  </div>
</template>

<script>
import confetti from 'canvas-confetti';

const VARIANTS = ['shockwave', 'cannons', 'fireworks'];
const COLORS = ['#19a974', '#ffd166', '#ffffff', '#ef476f', '#118ab2'];
const W_SHAPE = confetti.shapeFromText({text: 'W', scalar: 1.5, color: '#ffd166', fontFamily: 'sans-serif'});

export default {
  name: 'WinCelebration',
  data() {
    return {
      visible: false,
      variant: VARIANTS[0],
      timers: []
    };
  },
  beforeUnmount() {
    this.stop();
  },
  methods: {
    playRandom() {
      this.play(VARIANTS[Math.floor(Math.random() * VARIANTS.length)]);
    },
    play(variant) {
      this.stop();
      this.variant = variant;
      this.visible = true;
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        this.schedule(() => this.finish(), 800);
        return;
      }
      if (variant === 'shockwave') {
        this.playShockwave();
      } else if (variant === 'cannons') {
        this.playCannons();
      } else {
        this.playFireworks();
      }
      this.schedule(() => this.finish(), 2300);
    },
    playShockwave() {
      confetti({
        particleCount: 130,
        spread: 360,
        startVelocity: 52,
        gravity: 0.8,
        ticks: 105,
        origin: {x: 0.5, y: 0.48},
        colors: COLORS,
        shapes: ['star', W_SHAPE],
        scalar: 1.1,
        zIndex: 2201,
        disableForReducedMotion: true
      });
      this.schedule(() => confetti({
        particleCount: 55,
        spread: 120,
        startVelocity: 34,
        origin: {x: 0.5, y: 0.5},
        colors: COLORS,
        shapes: ['circle', 'star'],
        zIndex: 2201,
        disableForReducedMotion: true
      }), 320);
    },
    playCannons() {
      const fire = (origin, angle) => confetti({
        particleCount: 85,
        angle,
        spread: 58,
        startVelocity: 58,
        gravity: 0.95,
        ticks: 110,
        origin,
        colors: COLORS,
        shapes: ['star', W_SHAPE],
        scalar: 1.15,
        zIndex: 2201,
        disableForReducedMotion: true
      });
      fire({x: 0.02, y: 0.72}, 58);
      fire({x: 0.98, y: 0.72}, 122);
      this.schedule(() => {
        fire({x: 0.08, y: 0.58}, 48);
        fire({x: 0.92, y: 0.58}, 132);
      }, 420);
    },
    playFireworks() {
      const burst = (x, y) => confetti({
        particleCount: 65,
        spread: 360,
        startVelocity: 38,
        gravity: 0.65,
        ticks: 100,
        origin: {x, y},
        colors: COLORS,
        shapes: ['circle', 'star'],
        scalar: 0.95,
        zIndex: 2201,
        disableForReducedMotion: true
      });
      [[0.25, 0.35], [0.72, 0.28], [0.48, 0.56], [0.84, 0.52]].forEach(([x, y], index) => {
        this.schedule(() => burst(x, y), index * 260);
      });
    },
    schedule(action, delay) {
      this.timers.push(window.setTimeout(action, delay));
    },
    finish() {
      this.visible = false;
      this.timers = [];
      confetti.reset();
    },
    stop() {
      this.timers.forEach(timer => window.clearTimeout(timer));
      this.timers = [];
      this.visible = false;
      confetti.reset();
    }
  }
}
</script>

<style scoped>
.win-celebration {
  position: fixed;
  inset: 0;
  z-index: 2200;
  display: grid;
  place-items: center;
  overflow: hidden;
  pointer-events: none;
  background: rgba(20, 24, 27, 0.22);
}

.win-celebration-title {
  position: relative;
  z-index: 2;
  color: #ffffff;
  font-size: 7rem;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 0;
  text-shadow: 0 4px 0 #087f5b, 0 10px 24px rgba(0, 0, 0, 0.4);
}

.win-celebration-ring {
  position: absolute;
  width: 18rem;
  height: 18rem;
  border: 10px solid #ffd166;
  border-radius: 50%;
  opacity: 0;
}

.win-celebration--shockwave .win-celebration-title {
  animation: win-impact 2.2s ease-out both;
}

.win-celebration--shockwave .win-celebration-ring {
  animation: win-shockwave 1.25s ease-out both;
}

.win-celebration--cannons .win-celebration-title {
  animation: win-rise 2.2s cubic-bezier(0.2, 0.85, 0.25, 1) both;
}

.win-celebration--fireworks .win-celebration-title {
  animation: win-reveal 2.2s ease-in-out both;
}

@keyframes win-impact {
  0% { opacity: 0; transform: scale(3.2) rotate(-7deg); }
  18% { opacity: 1; transform: scale(0.88) rotate(2deg); }
  28%, 78% { opacity: 1; transform: scale(1) rotate(0); }
  100% { opacity: 0; transform: scale(1.18); }
}

@keyframes win-shockwave {
  0% { opacity: 0.9; transform: scale(0.15); }
  100% { opacity: 0; transform: scale(2.8); }
}

@keyframes win-rise {
  0% { opacity: 0; transform: translateY(7rem) scale(0.65); }
  28%, 78% { opacity: 1; transform: translateY(0) scale(1); }
  100% { opacity: 0; transform: translateY(-2rem) scale(1.08); }
}

@keyframes win-reveal {
  0%, 32% { opacity: 0; transform: scale(0.65); }
  48%, 82% { opacity: 1; transform: scale(1); }
  100% { opacity: 0; transform: scale(1.15); }
}

@media (max-width: 575px) {
  .win-celebration-title {
    font-size: 4rem;
  }
  .win-celebration-ring {
    width: 11rem;
    height: 11rem;
    border-width: 7px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .win-celebration {
    background: rgba(20, 24, 27, 0.12);
  }
  .win-celebration-title {
    animation: none !important;
  }
  .win-celebration-ring {
    display: none;
  }
}
</style>
