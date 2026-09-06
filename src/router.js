import { createWebHistory, createRouter } from "vue-router";
import Home from "@/components/Home.vue";
import WeightHistory from "@/components/WeightHistory";
import PhotoHistory from "@/components/PhotoHistory";
import HabitList from "@/components/HabitList";
import RoutineList from "@/components/RoutineList";
import BloodPressureHistory from "@/components/BloodPressureHistory";
import CalorieHistory from "@/components/CalorieHistory.vue";
import MoodHistory from "@/components/MoodHistory";
import SleepHistory from "@/components/SleepHistory.vue";
import SicknessHistory from "@/components/SicknessHistory.vue";
import Login from "@/components/Login.vue";
import WorkoutDiary from "@/components/WorkoutDiary.vue";
import Settings from "@/components/Settings.vue";
import Reflection from "@/components/Reflection.vue";
import BackPainEpisodeHistory from "@/components/BackPainEpisodeHistory.vue";
import LipidPanelHistory from "@/components/LipidPanelHistory.vue";
import PersonalRecords from "@/components/PersonalRecords.vue";
import MedicationList from "@/components/MedicationList.vue";
import GoalPlan from "@/components/GoalPlan.vue";
import Agenda from "@/components/Agenda.vue";
import MealEditor from "@/components/MealEditor.vue";

import DishRecipeEditor from '@/components/DishRecipeEditor.vue';

const routes = [
    {path: '/dishes/:id/edit', name: 'EditDish', component: DishRecipeEditor},
    {path: '/meals/new', name: 'NewMeal', component: MealEditor},
    {path: '/meals/:id/edit', name: 'EditMeal', component: MealEditor},
    {
        path: "/",
        name: "Home",
        component: Home,
    },
    {
        path: "/reflections",
        name: "Reflection",
        component: Reflection,
    },
    {
        path: "/weights",
        name: "WeightHistory",
        component: WeightHistory,
    },
    {
        path: "/photos",
        name: "PhotoHistory",
        component: PhotoHistory,
    },
    {
        path: "/pressures",
        name: "BloodPressureHistory",
        component: BloodPressureHistory,
    },
    {
        path: "/cholesterol",
        name: "LipidPanelHistory",
        component: LipidPanelHistory,
    },
    {
        path: "/moods",
        name: "MoodHistory",
        component: MoodHistory,
    },
    {
        path: "/calories",
        name: "CalorieHistory",
        component: CalorieHistory,
    },
    {
        path: "/sleep",
        name: "SleepHistory",
        component: SleepHistory,
    },
    {
        path: "/sicknesses",
        name: "SicknessHistory",
        component: SicknessHistory,
    },
    {
        path: "/back",
        name: "BackPainEpisodeHistory",
        component: BackPainEpisodeHistory,
    },
    {
        path: "/habits",
        name: "HabitList",
        component: HabitList,
    },
    {
        path: "/routines",
        name: "RoutineList",
        component: RoutineList,
    },
    {
        path: "/agenda",
        name: "Agenda",
        component: Agenda,
    },
    {
        path: "/medications",
        name: "MedicationList",
        component: MedicationList,
    },
    {
        path: "/workouts",
        name: "WorkoutDiary",
        component: WorkoutDiary,
    },
    {
        path: "/records",
        name: "PersonalRecords",
        component: PersonalRecords,
    },
    {
        path: "/settings",
        name: "Settings",
        component: Settings,
    },
    {
        path: "/plan",
        name: "GoalPlan",
        component: GoalPlan,
    },
    {
        path: "/login",
        name: "Login",
        component: Login,
    }
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

export default router;
