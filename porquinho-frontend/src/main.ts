import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/inter/700.css'
import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { registerAuthListener } from './services/session'

const app = createApp(App)

app.use(createPinia())
app.use(router)
registerAuthListener()

app.mount('#app')
