<template>
  <section class="recipe-editor">
    <h1>Edit dish</h1>
    <p v-if="loading" role="status">Loading dish…</p>
    <template v-else-if="error"><p role="alert">{{ error }}</p><Button label="Retry" class="p-button-outlined" @click="load" /><Button label="Back" class="p-button-outlined" @click="leave" /></template>
    <template v-else-if="recipe"><p>Changes apply to future uses. Existing meals stay unchanged.</p><DishRecipeForm ref="form" :recipe="recipe" :foods="foods" editableIngredients @saved="leave" @close="leave" /></template>
  </section>
</template>
<script>
import DishRecipeForm from './DishRecipeForm.vue';
import recipeService from '../services/DishRecipeService';
import mealService from '../services/MealService';
import {previousFoods} from '../model/Dish';
import {userState} from '../state';
export default {
  components: {DishRecipeForm},
  data() { return {state: userState(), loading: true, recipe: null, foods: [], error: ''}; },
  watch: {'state.user.profile': {immediate: true, handler(profile) { if (profile) this.load(); }}},
  mounted() { window.addEventListener('beforeunload', this.before_unload); },
  beforeUnmount() { window.removeEventListener('beforeunload', this.before_unload); },
  beforeRouteLeave() { return !this.$refs.form?.dirty || window.confirm('Discard unsaved dish changes?'); },
  methods: {
    async load() { this.loading = true; this.error = ''; try { const [recipe, meals] = await Promise.all([recipeService.get(this.$route.params.id), mealService.get_all()]); this.recipe = recipe; this.foods = previousFoods(meals); } catch { this.error = 'Unable to load this dish. It may have been deleted.'; } finally { this.loading = false; } },
    leave() { this.$router.push({path: '/calories', query: {tab: 'dishes'}}); },
    before_unload(event) { if (this.$refs.form?.dirty) { event.preventDefault(); event.returnValue = ''; } }
  }
};
</script>
<style scoped>
.recipe-editor { max-width: 60rem; padding: 1rem; margin: 0 auto; }
h1 { font-size: 1.5rem; }
</style>
