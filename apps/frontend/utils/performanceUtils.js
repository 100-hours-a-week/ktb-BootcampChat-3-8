/**
 * Performance utility functions for debouncing and throttling
 */

/**
 * Debounce function - delays execution until after wait milliseconds have elapsed
 * since the last time it was invoked.
 *
 * @param {Function} func - The function to debounce
 * @param {number} wait - The number of milliseconds to delay
 * @returns {Function} - The debounced function with a cancel method
 */
export const debounce = (func, wait) => {
  let timeout;

  const debounced = function(...args) {
    const context = this;
    clearTimeout(timeout);
    timeout = setTimeout(() => func.apply(context, args), wait);
  };

  debounced.cancel = () => {
    clearTimeout(timeout);
    timeout = null;
  };

  return debounced;
};

/**
 * Throttle function - ensures function is called at most once per specified time period
 *
 * @param {Function} func - The function to throttle
 * @param {number} wait - The number of milliseconds to throttle
 * @returns {Function} - The throttled function with a cancel method
 */
export const throttle = (func, wait) => {
  let timeout;
  let lastRan;

  const throttled = function(...args) {
    const context = this;

    if (!lastRan) {
      func.apply(context, args);
      lastRan = Date.now();
    } else {
      clearTimeout(timeout);
      timeout = setTimeout(() => {
        if (Date.now() - lastRan >= wait) {
          func.apply(context, args);
          lastRan = Date.now();
        }
      }, wait - (Date.now() - lastRan));
    }
  };

  throttled.cancel = () => {
    clearTimeout(timeout);
    timeout = null;
    lastRan = null;
  };

  return throttled;
};
